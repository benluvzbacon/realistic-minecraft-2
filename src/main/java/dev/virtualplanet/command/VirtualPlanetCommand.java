package dev.virtualplanet.command;

import com.mojang.brigadier.context.CommandContext;
import dev.virtualplanet.config.PlanetConfig;
import dev.virtualplanet.config.PlanetConfigManager;
import dev.virtualplanet.net.PlanetConfigSync;
import dev.virtualplanet.sky.LocalSkyModel;
import dev.virtualplanet.sky.LocalSkyView;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import net.minecraft.world.border.WorldBorder;

import java.util.List;
import java.util.Locale;

/**
 * {@code /virtualplanet info | debug | recalculate | reload}.
 *
 * <p>{@code info} reports what the <em>executing player</em> should see: their virtual longitude and
 * latitude, the local solar time, the sun's elevation and azimuth and the sunrise/sunset estimates.
 * It is computed with the same {@link LocalSkyModel} the client uses, from the shared world time and the
 * authoritative world border, so the numbers are exactly the ones the client renders.
 */
public final class VirtualPlanetCommand {
    private static final int ADMIN_PERMISSION_LEVEL = 2;

    private VirtualPlanetCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("virtualplanet")
                        .then(CommandManager.literal("info").executes(VirtualPlanetCommand::info))
                        .then(CommandManager.literal("debug")
                                .requires(source -> source.hasPermissionLevel(ADMIN_PERMISSION_LEVEL))
                                .executes(VirtualPlanetCommand::debug))
                        .then(CommandManager.literal("recalculate")
                                .requires(source -> source.hasPermissionLevel(ADMIN_PERMISSION_LEVEL))
                                .executes(VirtualPlanetCommand::recalculate))
                        .then(CommandManager.literal("reload")
                                .requires(source -> source.hasPermissionLevel(ADMIN_PERMISSION_LEVEL))
                                .executes(VirtualPlanetCommand::reload))));
    }

    private static int info(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("Virtual Planet: /virtualplanet info must be run by a player."));
            return 0;
        }
        LocalSkyView view = viewOf(player);
        PlanetConfig config = PlanetConfigManager.get();
        WorldBorder border = player.getWorld().getWorldBorder();
        double borderSize = border == null ? 0.0 : border.getSize();
        double circumference = config.effectiveCircumference(borderSize);
        source.sendFeedback(() -> Text.literal(String.format(Locale.ROOT,
                "Virtual Planet: enabled=%s, circumference=%.1f blocks, rotation period=%d ticks, revision=%d",
                config.enabled, circumference, config.rotationPeriodTicks,
                PlanetConfigSync.revision())), false);
        for (String line : view.toDebugString().split("\n")) {
            source.sendFeedback(() -> Text.literal(line), false);
        }
        return 1;
    }

    private static int debug(CommandContext<ServerCommandSource> context) {
        PlanetConfig config = PlanetConfigManager.get();
        config.debug = !config.debug;
        PlanetConfigManager.save();
        PlanetConfigSync.markDirty();
        boolean enabled = config.debug;
        context.getSource().sendFeedback(() -> Text.literal(
                "Virtual Planet: debug output " + (enabled ? "enabled" : "disabled")), true);
        return enabled ? 1 : 0;
    }

    private static int recalculate(CommandContext<ServerCommandSource> context) {
        PlanetConfigSync.markDirty();
        context.getSource().sendFeedback(() -> Text.literal(
                "Virtual Planet: recalculated - the configuration is being resent to every client, which "
                        + "drops their cached sky view."), true);
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player != null) {
            LocalSkyView view = viewOf(player);
            context.getSource().sendFeedback(() -> Text.literal(String.format(Locale.ROOT,
                    "Virtual Planet: local solar time %s, sun elevation %.2f deg, azimuth %.2f deg",
                    view.solarClock(), view.computedPosition().elevationDegrees(),
                    view.computedPosition().azimuthDegrees())), false);
        }
        return 1;
    }

    private static int reload(CommandContext<ServerCommandSource> context) {
        List<String> warnings = PlanetConfigManager.reload();
        PlanetConfigSync.markDirty();
        PlanetConfig config = PlanetConfigManager.get();
        context.getSource().sendFeedback(() -> Text.literal(String.format(Locale.ROOT,
                "Virtual Planet: configuration reloaded (enabled=%s, rotation period=%d ticks, %d repair(s))",
                config.enabled, config.rotationPeriodTicks, warnings.size())), true);
        for (String warning : warnings) {
            context.getSource().sendFeedback(() -> Text.literal("Virtual Planet: " + warning), false);
        }
        return 1;
    }

    /** The player's local sky, computed exactly like the client does. */
    public static LocalSkyView viewOf(ServerPlayerEntity player) {
        World world = player.getWorld();
        WorldBorder border = world.getWorldBorder();
        double centerX = border == null ? 0.0 : border.getCenterX();
        double centerZ = border == null ? 0.0 : border.getCenterZ();
        double size = border == null ? 0.0 : border.getSize();
        return new LocalSkyModel(PlanetConfigManager.get())
                .view(world.getTimeOfDay(), player.getX(), player.getZ(), 0.0, centerX, centerZ, size);
    }
}
