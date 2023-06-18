package codes.antti.auth.bluemap.demo;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.configuration.Configuration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class DemoPlugin extends JavaPlugin implements CommandExecutor, TabCompleter {
	private Configuration config;
	private LuckPerms lp;
	@Override
	public void onEnable() {
		saveDefaultConfig();
		this.config = getConfig();
		this.lp = LuckPermsProvider.get();

		PluginCommand command = Objects.requireNonNull(getCommand("demo"));
		command.setExecutor(this);
		command.setTabCompleter(this);

		getLogger().info("Demo plugin enabled");
	}

	@Override
	public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
		if (args.length == 1) {
			return Stream.of("list", "toggle")
					.filter(s -> s.contains(args[0].toLowerCase()))
					.collect(Collectors.toList());
		} else if (args.length == 2) {
			if (args[0].equals("toggle")) {
				return this.config.getStringList("nodes")
						.stream()
						.filter(s -> s.contains(args[1].toLowerCase()))
						.collect(Collectors.toList());
			}
		}
		return List.of();
	}

	@Override
	public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
		if (args.length == 0) return false;
		if (!(sender instanceof Player)) {
			sender.sendMessage("This command can only be used by players");
			return true;
		}

		Player player = (Player) sender;
		String subcommand  = args[0];

		if (subcommand.equals("list")) {
			if (args.length != 1) return false;
			StringBuilder s = new StringBuilder();
			s.append(ChatColor.GOLD).append(ChatColor.BOLD).append("Demo nodes:").append(ChatColor.RESET);
			for (String node : this.config.getStringList("nodes")) {
				s.append("\n  ").append(player.hasPermission(node) ? ChatColor.GREEN : ChatColor.RED).append(node);
			}
			s.append(ChatColor.RESET);
			player.sendMessage(s.toString());
			return true;
		} else if (subcommand.equals("toggle")) {
			if (args.length != 2) return false;
			String node = args[1];
			if (!this.config.getStringList("nodes").contains(node)) {
				player.sendMessage("Unknown permission node: " + node);
				return true;
			}
			User user = this.lp.getPlayerAdapter(Player.class).getUser(player);
			Node permNode = Node.builder(node).value(!player.hasPermission(node)).build();
			if (player.hasPermission(node)) {
				user.data().add(permNode);
				player.sendMessage(ChatColor.RED + String.valueOf(ChatColor.ITALIC) + "Removed permission: " + node + ChatColor.RESET);
			} else {
				user.data().add(permNode);
				player.sendMessage(ChatColor.GREEN + String.valueOf(ChatColor.ITALIC) + "Added permission: " + node + ChatColor.RESET);
			}
			player.sendMessage(ChatColor.GRAY + String.valueOf(ChatColor.ITALIC) + "Please allow some time for caches to update." + ChatColor.RESET);
			this.lp.getUserManager().saveUser(user);
			return true;
		}
		return false;
	}

	@Override
	public void onDisable() {
		getLogger().info("Demo plugin disabled");
	}
}
