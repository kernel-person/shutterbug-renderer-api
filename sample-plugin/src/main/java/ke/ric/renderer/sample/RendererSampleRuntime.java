package ke.ric.renderer.sample;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

/** API-free command and lifecycle boundary implemented by the lazily linked renderer runtime. */
interface RendererSampleRuntime extends AutoCloseable {
    boolean onCommand(CommandSender sender, Command command, String label, String[] args);

    @Override
    void close();
}
