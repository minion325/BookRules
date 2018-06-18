package me.minion325.bookrules;

import com.comphenix.tinyprotocol.TinyProtocol;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import net.minecraft.server.v1_8_R1.PacketDataSerializer;
import net.minecraft.server.v1_8_R1.PacketPlayInSettings;
import net.minecraft.server.v1_8_R1.PacketPlayOutCustomPayload;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v1_8_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class Main extends JavaPlugin implements Listener, CommandExecutor{

    private Map<String, ItemStack> localeItemMap = new HashMap<>();
    private ItemStack defaultBook;
    private int bookSlot;
    private TinyProtocol tinyProtocol;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        getCommand("bookrules").setExecutor(this);
        loadConfig();
        tinyProtocol = new TinyProtocol(this) {
            @Override
            public Object onPacketInAsync(Player sender, Channel channel, Object packet) {
                if (packet instanceof PacketPlayInSettings && tinyProtocol.hasInjected(sender)){
                    String locale = ((PacketPlayInSettings) packet).a();
                    CraftPlayer craftPlayer = (CraftPlayer) sender;
                    craftPlayer.getInventory().setHeldItemSlot(bookSlot);
                    craftPlayer.setItemInHand(getBookByLanguage(locale));
                    PacketPlayOutCustomPayload bookPacket = new PacketPlayOutCustomPayload("MC|BOpen", new PacketDataSerializer(Unpooled.buffer()));
                    plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                        craftPlayer.getHandle().playerConnection.sendPacket(bookPacket);
                    }, 30L);
                    uninjectPlayer(sender);
                }
                return packet;
            }
        };

    }

    private ItemStack getBookByLanguage(String locale){
        return localeItemMap.getOrDefault(locale, defaultBook);
    }

    private void loadConfig(){
        bookSlot = getConfig().getInt("bookslot");
        defaultBook = this.getConfig().getItemStack("default") == null ? new ItemStack(Material.WRITTEN_BOOK) : this.getConfig().getItemStack("default");
        for (String language: getConfig().getConfigurationSection("lang").getKeys(false)){
            ItemStack book = getConfig().getItemStack("lang." + language + ".item");
            for (String locale : getConfig().getStringList("lang." + language + ".locales")){
                localeItemMap.put(locale,book);
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if(!(sender instanceof Player)) {
            sender.sendMessage("You need to be a player to execute this command!");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("bookrules.admin")) {
            player.sendMessage(ChatColor.RED + "You do not have sufficient permissions to execute that command");
        }
        if (args.length == 0){
            sendHelpMessage(player);
            return true;
        }
        if (args.length > 1){
            player.sendMessage(ChatColor.RED + "Too many arguments");
            sendHelpMessage(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")){
            reloadConfig();
            return true;
        }

        if (args[0].equalsIgnoreCase("default")){
            if (player.getItemInHand().getType()!=Material.WRITTEN_BOOK) {
                player.sendMessage(ChatColor.RED + "You must be holding a written book in your hand to perform this command");
            }
            else {
                getConfig().set("default", player.getItemInHand());
                player.sendMessage(ChatColor.GRAY + "Default language set");
            }
        }

        if (args[0].equalsIgnoreCase("list")){
            player.sendMessage(ChatColor.GRAY + "Supported Languages");
            for (String language : getConfig().getConfigurationSection("lang").getKeys(false)){
                player.sendMessage(language);
            }
            return true;
        }
        else {
            if (player.getItemInHand().getType()!= Material.WRITTEN_BOOK){
                player.sendMessage(ChatColor.RED + "You must be holding a written book in your hand to perform this command");
            } else {
                getConfig().set("lang." + args[0].toLowerCase() + ".item", player.getItemInHand());
                if (!getConfig().getConfigurationSection("lang").getKeys(false).contains(args[0].toLowerCase())){
                    getConfig().set("lang." + args[0].toLowerCase() + "locales", Arrays.asList("add your", "locales here"));
                    player.sendMessage(ChatColor.GRAY + "New supported language added"+ChatColor.WHITE + args[0]);
                }
                else {
                    player.sendMessage("Book for " + args[0] + " updated.");
                }
                saveConfig();
            }
            return true;
        }
    }

    private void sendHelpMessage(Player player){
        player.sendMessage(new String[]{ChatColor.GRAY + "Usage:", ChatColor.GRAY + "/bookrules list   - Lists all the currently supported languages",
                ChatColor.GRAY + "/bookrules <language>   - Adds a new language to the supported language list with the book in hand. You have to manually specify the locales in the config file" ,
                ChatColor.GRAY + "/bookrules default   - Sets the default book",
                ChatColor.GRAY + "/bookrules reload   - Reloads the config"});
    }
}
