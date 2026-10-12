#!/usr/bin/env python3
"""Approximate symbol/arity checker for this project's own Java classes.

Some environments used for this mod have no `javac` (the JDK available there is
runtime only), and `tools/check_java_syntax.py` can only prove that the sources
parse.  This script adds a lightweight semantic pass: every call to a method or
constructor of a class *of this project*, and every field access on such a
class, must match a declared member with the same name and arity - including
members inherited from other classes of this project.

External types (``java.*``, ``net.minecraft.*``, ``org.junit.*`` ...) are not
modelled and are skipped, so this is a filter for typos in our own API, not a
replacement for `javac`.

Usage:  check_java_symbols.py src/main/java src/client/java src/test/java
"""

import os
import sys

try:
    import javalang
    from javalang import tree as T
except ImportError:  # pragma: no cover
    print("javalang is not installed; run: pip install javalang")
    sys.exit(2)

OBJECT_METHODS = {
    "equals", "hashCode", "toString", "getClass", "clone", "notify", "notifyAll", "wait",
    "compareTo", "iterator", "size", "isEmpty", "add", "get", "put", "contains", "compare",
    "apply", "accept", "run", "call", "test", "andThen", "compose", "values", "name",
    "ordinal", "keySet", "entrySet", "stream", "length", "charAt", "substring", "indexOf",
    "concat", "replace", "trim", "format", "valueOf", "parseInt", "parseDouble", "read",
    "write", "close", "flush", "append", "println", "print", "printf",
}


def type_name(node):
    """Simple name of a type node, flattening qualified/nested names."""
    if node is None:
        return None
    if isinstance(node, str):
        return node.split(".")[-1]
    parts = []
    current = node
    while current is not None:
        name = getattr(current, "name", None)
        if name:
            parts.append(name)
        current = getattr(current, "sub_type", None)
    if not parts:
        return None
    return parts[-1].split(".")[-1]


def walk(node):
    """Yield every node in the subtree; does not descend into nested types."""
    if node is None:
        return
    if isinstance(node, list):
        for item in node:
            for found in walk(item):
                yield found
        return
    if not isinstance(node, javalang.ast.Node):
        return
    if isinstance(node, T.TypeDeclaration):
        return
    yield node
    for child in node.children:
        for found in walk(child):
            yield found


def _add_name(names, parameter):
    """Register a parameter-like node, tolerating the different shapes javalang uses."""
    name = getattr(parameter, "name", None)
    if isinstance(name, str):
        names.add(name)
        return
    member = getattr(parameter, "member", None)  # javalang models `x -> ...` parameters as references
    if isinstance(member, str):
        names.add(member)
        return
    if isinstance(parameter, str):
        names.add(parameter)


def members(body):
    """Type body members, unwrapping the tuples javalang uses for enum constants."""
    for member in body or []:
        if isinstance(member, tuple):
            member = member[-1]
        if isinstance(member, javalang.ast.Node):
            yield member


def arity(parameters):
    return len(parameters or [])


class TypeInfo(object):
    def __init__(self, name, path, line, outer=None):
        self.name = name
        self.path = path
        self.line = line
        self.outer = outer
        self.methods = {}         # name -> set of arities
        self.ctor_arities = set()
        self.declared_ctor = False
        self.fields = set()
        self.supers = []          # simple names
        self.initializers = {}    # field name -> simple type name

    def all_methods(self, index, seen=None):
        seen = seen or set()
        if self.name in seen:
            return {}
        seen.add(self.name)
        merged = {}
        for name, arities in self.methods.items():
            merged.setdefault(name, set()).update(arities)
        for super_name in self.supers:
            sup = index.get(super_name)
            if sup is not None:
                for name, arities in sup.all_methods(index, seen).items():
                    merged.setdefault(name, set()).update(arities)
        return merged

    def all_fields(self, index, seen=None):
        seen = seen or set()
        if self.name in seen:
            return set()
        seen.add(self.name)
        merged = set(self.fields) | set(self.initializers)
        for super_name in self.supers:
            sup = index.get(super_name)
            if sup is not None:
                merged |= sup.all_fields(index, seen)
        return merged

    def all_field_types(self, index, seen=None):
        seen = seen or set()
        if self.name in seen:
            return {}
        seen.add(self.name)
        merged = dict(self.initializers)
        for super_name in self.supers:
            sup = index.get(super_name)
            if sup is not None:
                merged.update(sup.all_field_types(index, seen))
        return merged


def collect(roots):
    index = {}
    parsed = []
    for path in iter_java_files(roots):
        with open(path, "r", encoding="utf-8") as handle:
            code = handle.read()
        try:
            tree = javalang.parse.parse(code)
        except javalang.parser.JavaSyntaxError as error:
            print("SYNTAX {0}: {1}".format(path, error))
            continue
        parsed.append((path, tree))
        for _path, node in tree.filter(T.TypeDeclaration):
            register(node, path, index, None)
    return index, parsed


def register(node, path, index, outer):
    info = TypeInfo(node.name, path, node.position.line, outer)
    info.supers.append(type_name(getattr(node, "extends", None)))
    for interface in (getattr(node, "implements", None) or []):
        info.supers.append(type_name(interface))
    info.supers = [name for name in info.supers if name]
    for member in members(node.body):
        if isinstance(member, T.MethodDeclaration):
            info.methods.setdefault(member.name, set()).add(arity(member.parameters))
        elif isinstance(member, T.ConstructorDeclaration):
            info.declared_ctor = True
            info.ctor_arities.add(arity(member.parameters))
        elif isinstance(member, T.FieldDeclaration):
            for declarator in member.declarators:
                info.fields.add(declarator.name)
                info.initializers[declarator.name] = type_name(member.type)
        elif isinstance(member, T.ConstantDeclaration):
            for declarator in member.declarators:
                info.fields.add(declarator.name)
        elif isinstance(member, T.EnumConstantDeclaration):
            info.fields.add(member.name)
        elif isinstance(member, T.TypeDeclaration):
            register(member, path, index, info.name)
    index[node.name] = info
    return info


def iter_java_files(roots):
    for root in roots:
        if os.path.isfile(root) and root.endswith(".java"):
            yield root
            continue
        for dirpath, _dirnames, filenames in os.walk(root):
            for filename in sorted(filenames):
                if filename.endswith(".java"):
                    yield os.path.join(dirpath, filename)


def static_imports(paths):
    names = set()
    for path in paths:
        with open(path, "r", encoding="utf-8") as handle:
            for line in handle:
                line = line.strip()
                if line.startswith("import static ") and line.endswith(";"):
                    names.add(line[len("import static "):-1].split(".")[-1])
    return names


class Checker(object):
    def __init__(self, index, statics):
        self.index = index
        self.statics = statics
        self.issues = []

    def report(self, path, node, message):
        line = node.position.line if getattr(node, "position", None) is not None else "?"
        self.issues.append("{0}:{1}: {2}".format(path, line, message))

    def resolves(self, target, name, argc):
        info = self.index.get(target)
        if info is None:
            return True  # not one of our types: not modelled
        methods = info.all_methods(self.index)
        if name in methods:
            return argc in methods[name]
        if name in OBJECT_METHODS or name in self.statics:
            return True
        if any(super_name not in self.index for super_name in info.supers):
            return True  # external supertype may declare it
        if info.outer is not None and self.resolves(info.outer, name, argc):
            return True
        return False

    def harvest_names(self, node, info, variables):
        names = set(info.all_fields(self.index)) | set(self.statics)
        for member in members(node.body):
            if isinstance(member, (T.MethodDeclaration, T.ConstructorDeclaration)):
                for parameter in member.parameters or []:
                    names.add(parameter.name)
                for found in walk(member):
                    if isinstance(found, T.VariableDeclaration):
                        for declarator in found.declarators:
                            names.add(declarator.name)
                    elif isinstance(found, T.CatchClause):
                        candidates = list(getattr(found, "parameters", None) or [])
                        if getattr(found, "parameter", None) is not None:
                            candidates.append(found.parameter)
                        for parameter in candidates:
                            _add_name(names, parameter)
                    elif isinstance(found, T.LambdaExpression):
                        for parameter in (found.parameters or []):
                            _add_name(names, parameter)
                    elif isinstance(found, T.EnhancedForControl):
                        variable = getattr(found, "variable", None)
                        if variable is not None:
                            names.add(variable.name)
            elif isinstance(member, T.FieldDeclaration):
                for declaration in [member]:
                    for declarator in declaration.declarators:
                        names.add(declarator.name)
                        for found in walk(declarator.initializer):
                            if isinstance(found, T.LambdaExpression):
                                for parameter in (found.parameters or []):
                                    _add_name(names, parameter)
        return names

    def check_type(self, node, path):
        info = self.index[node.name]
        variables = {}
        self.receivers = set()
        # `Type::method` references are parsed as two qualifier-free MemberReferences; their parts are
        # not variables and must not be checked as such.
        self.method_ref_parts = set()
        for member in members(node.body):
            if isinstance(member, (T.MethodDeclaration, T.ConstructorDeclaration, T.FieldDeclaration)):
                for found in walk(member):
                    if isinstance(found, T.MethodReference):
                        for part in (found.expression, found.method):
                            for nested in walk(part):
                                self.method_ref_parts.add(id(nested))
                        self.method_ref_parts.add(id(found.expression))
                        self.method_ref_parts.add(id(found.method))
        self.known_names = self.harvest_names(node, info, variables)
        for member in members(node.body):
            if isinstance(member, (T.MethodDeclaration, T.ConstructorDeclaration, T.FieldDeclaration)):
                for found in walk(member):
                    if isinstance(found, (T.MethodInvocation, T.ClassCreator)):
                        for selector in (found.selectors or []):
                            self._mark_receivers(selector, self.receivers)
        for member in members(node.body):
            if isinstance(member, (T.MethodDeclaration, T.ConstructorDeclaration)):
                for parameter in member.parameters or []:
                    variables[parameter.name] = type_name(parameter.type)
                body = member.body if isinstance(member, T.MethodDeclaration) else member.body
                for statement in walk(body):
                    if isinstance(statement, T.VariableDeclaration):
                        for declarator in statement.declarators:
                            variables[declarator.name] = type_name(statement.type)
            elif isinstance(member, T.FieldDeclaration):
                for declarator in member.declarators:
                    variables[declarator.name] = type_name(member.type)
                    for node2 in walk(declarator.initializer):
                        self.check_usage(node2, path, info, variables)

        for member in members(node.body):
            if isinstance(member, (T.MethodDeclaration, T.ConstructorDeclaration, T.FieldDeclaration)):
                for found in walk(member):
                    self.check_usage(found, path, info, variables)

        for member in members(node.body):
            if isinstance(member, T.TypeDeclaration):
                self.check_type(member, path)

    def target_of(self, qualifier, info, variables):
        qualifier = qualifier.split(".")[-1]
        if qualifier in self.index:
            return self.index[qualifier].name
        if qualifier in variables:
            name = variables[qualifier]
            if name in self.index:
                return name
        # inherited field of the enclosing type used as a qualifier
        field_types = info.all_field_types(self.index)
        if qualifier in field_types and field_types[qualifier] in self.index:
            return field_types[qualifier]
        return None

    def _mark_receivers(self, node, receivers):
        """Calls that are the receiver of another call (`x.y().z()`) have an unknown type."""
        if isinstance(node, (T.MethodInvocation, T.ClassCreator, T.MemberReference,
                             T.ArraySelector, T.SuperMethodInvocation, T.This)):
            receivers.add(id(node))
        for selector in (getattr(node, "selectors", None) or []):
            self._mark_receivers(selector, receivers)

    def check_usage(self, node, path, info, variables):
        if isinstance(node, T.MethodInvocation):
            argc = len(node.arguments or [])
            if not node.qualifier and id(node) in self.receivers:
                return  # chained call such as x.y().z(): the receiver type is unknown
            if node.qualifier:
                target = self.target_of(node.qualifier, info, variables)
                if target is None:
                    return
                if not self.resolves(target, node.member, argc):
                    declared = sorted(self.index[target].all_methods(self.index).get(node.member, [])) or "none"
                    self.report(path, node, "unresolved method {0}.{1}/{2} (declared arities: {3})".format(
                        target, node.member, argc, declared))
                return
            enclosing = info
            while enclosing is not None:
                if self.resolves(enclosing.name, node.member, argc):
                    return
                enclosing = self.index.get(enclosing.outer) if enclosing.outer else None
            self.report(path, node, "unresolved method {0}.{1}/{2} (no enclosing type declares it)".format(
                info.name, node.member, argc))
        elif isinstance(node, T.ClassCreator):
            name = type_name(node.type)
            target = self.index.get(name)
            if target is None:
                return
            argc = len(node.arguments or [])
            if target.declared_ctor:
                if argc not in target.ctor_arities:
                    self.report(path, node, "unresolved constructor new {0}/{1} (declared arities: {2})".format(
                        name, argc, sorted(target.ctor_arities)))
            elif argc != 0:
                self.report(path, node, "new {0}/{1} but only the implicit no-arg constructor exists".format(
                    name, argc))
        elif isinstance(node, T.MemberReference):
            if not node.qualifier:
                name = node.member
                if id(node) in self.method_ref_parts:
                    return
                if name in self.known_names or name in OBJECT_METHODS:
                    return
                if getattr(node, "qualifier", None) is None and name in ("length", "class"):
                    return
                if id(node) in self.receivers:
                    return
                self.report(path, node, "unresolved variable {0} in {1}".format(name, info.name))
                return
            target = self.target_of(node.qualifier, info, variables)
            if target is None:
                return
            target_info = self.index[target]
            if node.member in target_info.all_fields(self.index):
                return
            if any(super_name not in self.index for super_name in target_info.supers):
                return
            if node.member.isupper():  # possibly an enum constant
                return
            self.report(path, node, "unresolved field {0}.{1}".format(target, node.member))


def main(argv):
    if len(argv) < 2:
        print(__doc__)
        return 2
    paths = list(iter_java_files(argv[1:]))
    index, parsed = collect(paths)
    statics = static_imports(paths)
    checker = Checker(index, statics)
    for path, tree in parsed:
        for node in tree.types:  # top level types; nested ones are handled recursively
            if isinstance(node, T.TypeDeclaration):
                checker.check_type(node, path)
    for issue in checker.issues:
        print("ISSUE " + issue)
    print("{0} type(s) indexed, {1} file(s) checked, {2} issue(s)".format(
        len(index), len(parsed), len(checker.issues)))
    return 1 if checker.issues else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
