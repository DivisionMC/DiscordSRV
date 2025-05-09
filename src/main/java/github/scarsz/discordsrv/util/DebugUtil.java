/*
 * DiscordSRV - https://github.com/DiscordSRV/DiscordSRV
 *
 * Copyright (C) 2016 - 2024 Austin "Scarsz" Shapiro
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 */

package github.scarsz.discordsrv.util;

import alexh.weak.Dynamic;
import com.github.kevinsawicki.http.HttpRequest;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.Gson;
import github.scarsz.configuralize.Language;
import github.scarsz.discordsrv.Debug;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.api.events.DebugReportedEvent;
import github.scarsz.discordsrv.hooks.PluginHook;
import github.scarsz.discordsrv.hooks.SkriptHook;
import github.scarsz.discordsrv.hooks.VaultHook;
import github.scarsz.discordsrv.hooks.chat.TownyChatHook;
import github.scarsz.discordsrv.listeners.DiscordDisconnectListener;
import github.scarsz.discordsrv.modules.voice.VoiceModule;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.requests.CloseCode;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scheduler.BukkitWorker;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class DebugUtil {

    public static final List<String> SENSITIVE_OPTIONS = Arrays.asList(
        "BotToken",
        "Experiment_JdbcAccountLinkBackend", "Experiment_JdbcUsername", "Experiment_JdbcPassword",
        "ProxyHost", "ProxyPort", "ProxyUser", "ProxyPassword"
    );
    public static int initializationCount = 0;

    public static String run(String requester) {
        return run(requester, 256);
    }

    public static String run(String requester, int aesBits) {
        List<Map<String, String>> files = new LinkedList<>();
        try {
            String debugInformation = getDebugInformation();
            boolean noIssues = debugInformation.contains("No issues detected automatically");
            Runnable addDebugInfo = () -> files.add(fileMap("debug-info.txt", "Potential issues in the installation", debugInformation));
            if (!noIssues) addDebugInfo.run();
            files.add(fileMap("discordsrv-info.txt", "general information about the plugin", String.join("\n", new String[]{
                    "Version information:",
                    "   plugin version: " + DiscordSRV.getPlugin(),
                    "   config version: " + DiscordSRV.config().getString("ConfigVersion"),
                    "   build date: " + ManifestUtil.getManifestValue("Build-Date"),
                    "   build git revision: " + ManifestUtil.getManifestValue("Git-Revision"),
                    "   build number: " + ManifestUtil.getManifestValue("Build-Number"),
                    "   build origin: " + ManifestUtil.getManifestValue("Build-Origin"),
                    "Plugin status:",
                    "   jda status: " + (DiscordUtil.getJda() != null && DiscordUtil.getJda().getGatewayPing() != -1 ? DiscordUtil.getJda().getStatus().name() + " / " + DiscordUtil.getJda().getGatewayPing() + "ms" : "build not finished"),
                    "   channels: " + DiscordSRV.getPlugin().getChannels(),
                    "   console channel: " + DiscordSRV.getPlugin().getConsoleChannel(),
                    "   main chat channel: " + DiscordSRV.getPlugin().getMainChatChannel() + " -> " + DiscordSRV.getPlugin().getMainTextChannel(),
                    "   main guild: " + DiscordSRV.getPlugin().getMainGuild(),
                    "Environmental variables:",
                    "   discord main guild roles: " + (DiscordSRV.getPlugin().getMainGuild() == null ? "invalid main guild" : DiscordSRV.getPlugin().getMainGuild().getRoles().stream().map(Role::toString).collect(Collectors.toList())),
                    "   discord server owner: " + (DiscordSRV.getPlugin().getMainGuild() == null ? "invalid main guild" : DiscordSRV.getPlugin().getMainGuild().getOwner()),
                    "   vault groups: " + Arrays.toString(VaultHook.getGroups()),
                    "   PlaceholderAPI expansions: " + getInstalledPlaceholderApiExpansions(),
                    "   Skripts: " + String.join(", ", SkriptHook.getSkripts()),
                    "   /discord command executor: " + (Bukkit.getServer().getPluginCommand("discord") != null ? Bukkit.getServer().getPluginCommand("discord").getPlugin() : ""),
                    "   hooked plugins: " + DiscordSRV.getPlugin().getPluginHooks().stream().map(PluginHook::getPlugin).filter(Objects::nonNull).map(Object::toString).collect(Collectors.joining(", ")),
                    "Threads:",
                    "   channel topic updater -> alive: " + (DiscordSRV.getPlugin().getChannelTopicUpdater() != null && DiscordSRV.getPlugin().getChannelTopicUpdater().isAlive()),
                    "   server watchdog -> alive: " + (DiscordSRV.getPlugin().getServerWatchdog() != null && DiscordSRV.getPlugin().getServerWatchdog().isAlive()),
                    "   nickname updater -> alive: " + (DiscordSRV.getPlugin().getNicknameUpdater() != null && DiscordSRV.getPlugin().getNicknameUpdater().isAlive()),
                    "   presence updater -> alive: " + (DiscordSRV.getPlugin().getPresenceUpdater() != null && DiscordSRV.getPlugin().getPresenceUpdater().isAlive()),
                    "Guilds:" + listGuilds()
            })));
            files.add(fileMap("relevant-lines-from-server.log", "lines from the server console containing \"discordsrv\"", getRelevantLinesFromServerLog()));
            files.add(fileMap("config.yml", "raw plugins/DiscordSRV/config.yml", FileUtils.readFileToString(DiscordSRV.getPlugin().getConfigFile(), StandardCharsets.UTF_8)));
            files.add(fileMap("config-active.yml", "active plugins/DiscordSRV/config.yml", getActiveConfig()));
            files.add(fileMap("messages.yml", "raw plugins/DiscordSRV/messages.yml", FileUtils.readFileToString(DiscordSRV.getPlugin().getMessagesFile(), StandardCharsets.UTF_8)));
            files.add(fileMap("voice.yml", "raw plugins/DiscordSRV/voice.yml", FileUtils.readFileToString(DiscordSRV.getPlugin().getVoiceFile(), StandardCharsets.UTF_8)));
            files.add(fileMap("linking.yml", "raw plugins/DiscordSRV/linking.yml", FileUtils.readFileToString(DiscordSRV.getPlugin().getLinkingFile(), StandardCharsets.UTF_8)));
            files.add(fileMap("synchronization.yml", "raw plugins/DiscordSRV/synchronization.yml", FileUtils.readFileToString(DiscordSRV.getPlugin().getSynchronizationFile(), StandardCharsets.UTF_8)));
            files.add(fileMap("alerts.yml", "raw plugins/DiscordSRV/alerts.yml", FileUtils.readFileToString(DiscordSRV.getPlugin().getAlertsFile(), StandardCharsets.UTF_8)));
            files.add(fileMap("server-info.txt", null, getServerInfo()));
            files.add(fileMap("logger-details.txt", null, getLoggerInfo()));
            files.add(fileMap("registered-listeners.txt", "list of registered listeners for Bukkit events DiscordSRV uses", getRegisteredListeners()));
            files.add(fileMap("permissions.txt", null, getPermissions()));
            files.add(fileMap("threads.txt", "Threads with DiscordSRV in the name or that have trace elements with DiscordSRV's classes", getThreads()));
            files.add(fileMap("system-info.txt", null, getSystemInfo()));
            if (noIssues) addDebugInfo.run();
        } catch (Exception e) {
            DiscordSRV.error(e);
            return "Failed to collect debug information: " + e.getMessage() + ". Check the console for further details.";
        }

        return uploadReport(files, aesBits, requester);
    }

    private static String listGuilds() {
        if (DiscordUtil.getJda() == null) return "\n   null JDA";
        String list = "";
        for (Guild server : DiscordUtil.getJda().getGuilds()) {
            list += "\n   " + server + ":  [";
            for (TextChannel channel : server.getTextChannels()) list += channel + ", ";
            list += "]";
        }
        return list;
    }

    private static Map<String, String> fileMap(String name, String description, String content) {
        Map<String, String> map = new HashMap<>();
        map.put("name", name);
        map.put("description", description);
        map.put("content", content);
        map.put("type", "text/plain");
        return map;
    }

    private static String getActiveConfig() {
        try {
            Dynamic activeConfig = DiscordSRV.config().getProvider("config").getValues();
            StringBuilder stringBuilder = new StringBuilder(500);
            Iterator<Dynamic> iterator = activeConfig.allChildren().iterator();
            while (iterator.hasNext()) {
                Dynamic child = iterator.next();
                if (child.allChildren().count() == 0) {
                    stringBuilder.append(child.key().asObject()).append(": ").append(child.asObject());
                } else {
                    StringJoiner childJoiner = new StringJoiner(", ");

                    Iterator<Dynamic> childIterator = child.allChildren().iterator();
                    while (childIterator.hasNext()) {
                        Dynamic grandchild = childIterator.next();
                        childJoiner.add("- " + grandchild.asObject());
                    }

                    stringBuilder.append(child.key().asObject()).append(": ").append(childJoiner);
                }
                stringBuilder.append("\n");
            }
            return stringBuilder.toString();
        } catch (Exception e) {
            return "Failed to get parsed config: " + e.getMessage() + "\n" + ExceptionUtils.getStackTrace(e);
        }
    }

    private static String getInstalledPlaceholderApiExpansions() {
        if (!PluginUtil.pluginHookIsEnabled("placeholderapi")) return "PlaceholderAPI not hooked/no expansions installed";
        File[] extensionFiles = new File(DiscordSRV.getPlugin().getDataFolder().getParentFile(), "PlaceholderAPI/expansions").listFiles();
        if (extensionFiles == null) return "PlaceholderAPI/expansions is not directory/IO error";
        return Arrays.stream(extensionFiles).map(File::getName).collect(Collectors.joining(", "));
    }

    private static String getRelevantLinesFromServerLog() {
        List<String> output = new LinkedList<>();
        try {
            FileReader fr = new FileReader(new File("logs/latest.log"));
            BufferedReader br = new BufferedReader(fr);
            boolean done = false;
            while (!done) {
                String line = br.readLine();
                if (line == null) done = true;
                else if (
                    line.toLowerCase().contains("discordsrv") && !line.toLowerCase().contains("[discordsrv] chat:")
                    || line.toLowerCase().contains(" /discord")
                ) output.add(DiscordUtil.aggressiveStrip(line));
            }
        } catch (IOException e) {
            DiscordSRV.error(e);
        }

        return String.join("\n", output);
    }

    private static String getServerInfo() {
        List<String> output = new LinkedList<>();

        List<String> plugins = Arrays.stream(Bukkit.getPluginManager().getPlugins()).map(Object::toString).sorted().collect(Collectors.toList());

        output.add("server players: " + PlayerUtil.getOnlinePlayers().size() + "/" + Bukkit.getMaxPlayers());
        output.add("server plugins: " + plugins);
        output.add("");
        output.add("Minecraft version: " + Bukkit.getVersion());
        output.add("Bukkit API version: " + Bukkit.getBukkitVersion());
        output.add("Server online mode: " + Bukkit.getOnlineMode());

        return String.join("\n", output);
    }

    private static String getLoggerInfo() {
        List<String> output = new LinkedList<>();

        try {
            LoggerContext config = ((LoggerContext) LogManager.getContext(false));
            output.add("Log level: " + config.getConfiguration().getLoggerConfig(LogManager.ROOT_LOGGER_NAME).getLevel());

            org.apache.logging.log4j.core.Logger rootLogger = ((org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager.getRootLogger());

            List<String> filters = new ArrayList<>();
            Iterator<org.apache.logging.log4j.core.Filter> filterIterator = rootLogger.getFilters();
            while (filterIterator.hasNext()) {
                Object next = filterIterator.next();
                filters.add(next.getClass().getName() + ": " + next);
            }
            output.add("Filters: " + String.join(", ", filters));

            List<String> appenders = new ArrayList<>();
            for (Map.Entry<String, org.apache.logging.log4j.core.Appender> entry : rootLogger.getAppenders().entrySet()) {
                appenders.add(entry.getKey() + ": " + entry.getValue().getName() + " (" + entry.getValue().getClass().getName() + ")");
            }
            output.add("Appenders: " + String.join(", ", appenders));
        } catch (Throwable t) {
            output.add("Failed to log debug message for logging");
            output.add(ExceptionUtils.getMessage(t));
        }
        return String.join("\n", output);
    }

    private static String getDebugInformation() {
        List<Message> messages = new ArrayList<>();

        if (initializationCount > 1) {
            messages.add(new Message(Message.Type.PLUGIN_RELOADED));
        }

        if (DiscordUtil.getJda() == null) {
            if (DiscordSRV.invalidBotToken || DiscordDisconnectListener.mostRecentCloseCode == CloseCode.AUTHENTICATION_FAILED) {
                messages.add(new Message(Message.Type.INVALID_BOT_TOKEN));
            } else if (DiscordDisconnectListener.mostRecentCloseCode == CloseCode.DISALLOWED_INTENTS) {
                messages.add(new Message(Message.Type.DISALLOWED_INTENTS));
            } else {
                messages.add(new Message(Message.Type.NOT_CONNECTED));
            }
        } else if (DiscordUtil.getJda().getGuilds().isEmpty()) {
            messages.add(new Message(Message.Type.NOT_IN_ANY_SERVERS));
        }

        if (DiscordUtil.getJda() != null) {
            if (DiscordSRV.getPlugin().getMainTextChannel() == null) {
                if (DiscordSRV.getPlugin().getConsoleChannel() == null) {
                    messages.add(new Message(Message.Type.NO_CHANNELS_LINKED));
                } else {
                    messages.add(new Message(Message.Type.NO_CHAT_CHANNELS_LINKED));
                }
            }

            for (Map.Entry<String, String> entry : DiscordSRV.getPlugin().getChannels().entrySet()) {
                TextChannel textChannel = DiscordUtil.getTextChannelById(entry.getValue());
                if (textChannel == null || !textChannel.getId().equals(entry.getValue())) {
                    messages.add(new Message(Message.Type.INVALID_CHANNEL, "{" + entry.getKey() + ":" + entry.getValue() + "}"));
                    continue;
                }

                String configName = entry.getKey();
                String discordName = textChannel.getName();
                // contains non-alphanumeric & -whitespace characters (not a-z, 0-9 or whitespaces), "mc", "minecraft" or "chat" or is "global"
                if (configName.equals(discordName) && (!configName.replaceAll("[\\w\\d\\s]", "").isEmpty()
                        || configName.contains("mc") || configName.contains("minecraft") || configName.contains("chat")) && !configName.equals("global")) {
                    messages.add(new Message(Message.Type.SAME_CHANNEL_NAME, entry.getKey()));
                }
            }
        }

        String consoleChannelId = DiscordSRV.config().getString("DiscordConsoleChannelId");
        if (consoleChannelId != null && !consoleChannelId.matches("^0*$")
                && DiscordSRV.getPlugin().getChannels().values().stream().filter(Objects::nonNull).anyMatch(channelId -> channelId.equals(consoleChannelId))) {
            messages.add(new Message(Message.Type.CONSOLE_AND_CHAT_SAME_CHANNEL));
        }

        String roleName = DiscordSRV.config().getStringElse("MinecraftDiscordAccountLinkedRoleNameToAddUserTo", null);
        if (DiscordUtil.getJda() != null && roleName != null) {
            try {
                Role role = DiscordUtil.resolveRole(roleName);
                if (role != null && DiscordSRV.getPlugin().getGroupSynchronizables().values().stream().anyMatch(roleId -> roleId.equals(role.getId()))) {
                    messages.add(new Message(Message.Type.LINKED_ROLE_GROUP_SYNC));
                }
            } catch (Throwable ignored) {}
        }

        if (PluginUtil.pluginHookIsEnabled("TownyChat")) {
            try {
                String mainChannelName = TownyChatHook.getMainChannelName();
                if (mainChannelName != null && !DiscordSRV.getPlugin().getChannels().containsKey(mainChannelName)) {
                    messages.add(new Message(Message.Type.NO_TOWNY_MAIN_CHANNEL, mainChannelName));
                }
            } catch (Throwable ignored) {
                // didn't work
            }
        }

        if (!DiscordSRV.config().getBooleanElse("RespectChatPlugins", true)) {
            messages.add(new Message(Message.Type.RESPECT_CHAT_PLUGINS));
        }

        if (!Debug.anyEnabled()) {
            messages.add(new Message(Message.Type.DEBUG_MODE_NOT_ENABLED));
        }

        if (DiscordSRV.updateIsAvailable) {
            messages.add(new Message(Message.Type.UPDATE_AVAILABLE));
        } else if (!DiscordSRV.updateChecked || DiscordSRV.isUpdateCheckDisabled()) {
            messages.add(new Message(Message.Type.UPDATE_CHECK_DISABLED));
        }

        StringBuilder stringBuilder = new StringBuilder();
        if (messages.isEmpty()) {
            stringBuilder.append("No issues detected automatically\n");
        } else {
            messages.stream().sorted((one, two) -> Boolean.compare(one.isWarning(), two.isWarning())).forEach(message ->
                    stringBuilder.append(message.isWarning() ? "[Warn] " : "[Error] ").append(message.getMessage()).append("\n"));
        }
        stringBuilder.append("\nFailedTests: [").append(messages.stream().map(Message::getTypeName).collect(Collectors.joining(", "))).append(']');
        stringBuilder.append("\nDebuggerCategories: [").append(String.join(", ", DiscordSRV.getPlugin().getDebuggerCategories())).append(']');

        return stringBuilder.toString();
    }

    @SuppressWarnings("deprecation")
    private static String getRegisteredListeners() {
        List<String> output = new LinkedList<>();
        List<Class<?>> listenedClasses = new ArrayList<>();

        try {
            listenedClasses.add(Class.forName("io.papermc.paper.event.player.AsyncChatEvent"));
            listenedClasses.add(Class.forName("io.papermc.paper.event.player.ChatEvent"));
        } catch (ClassNotFoundException ignored) {
            output.add("(Async)ChatEvent not available.");
        }

        listenedClasses.addAll(Arrays.asList(
                AsyncPlayerChatEvent.class,
                PlayerChatEvent.class,
                PlayerJoinEvent.class,
                PlayerQuitEvent.class,
                PlayerDeathEvent.class,
                AsyncPlayerPreLoginEvent.class,
                PlayerLoginEvent.class
        ));

        try {
            listenedClasses.add(Class.forName("org.bukkit.event.player.PlayerAdvancementDoneEvent"));
        } catch (ClassNotFoundException ignored) {
            try {
                listenedClasses.add(Class.forName("org.bukkit.event.player.PlayerAchievementAwardedEvent"));
            } catch (ClassNotFoundException ignore) {
                output.add("PlayerAdvancementDoneEvent and PlayerAchievementAwardedEvent both unavailable??");
            }
        }

        for (Class<?> listenedClass : listenedClasses) {
            try {
                Class<?> effectiveClass = null;
                Method getHandlerList;
                try {
                    getHandlerList = listenedClass.getDeclaredMethod("getHandlerList");
                } catch (NoSuchMethodException ignored) {
                    // Try super class
                    Class<?> superClass = listenedClass.getSuperclass();
                    getHandlerList = superClass.getDeclaredMethod("getHandlerList");
                    effectiveClass = superClass;
                }

                HandlerList handlerList = (HandlerList) getHandlerList.invoke(null);
                List<RegisteredListener> registeredListeners = Arrays.stream(handlerList.getRegisteredListeners())
                        .filter(registeredListener -> !registeredListener.getPlugin().getName().equalsIgnoreCase("DiscordSRV"))
                        .sorted(Comparator.comparing(RegisteredListener::getPriority)).collect(Collectors.toList());

                if (registeredListeners.isEmpty()) {
                    output.add("No " + listenedClass + " listeners registered.");
                } else {
                    output.add("Registered " + (listenedClass.isAnnotationPresent(Deprecated.class) ? "(DEPRECATED) " : "")
                            + listenedClass.getSimpleName() + (effectiveClass != null ? " (" + effectiveClass.getSimpleName() + ")" : "")
                            + " listeners (" + registeredListeners.size() + "): " + registeredListeners.stream()
                                .map(listener -> listener.getPlugin().getName())
                                .distinct().sorted().collect(Collectors.joining(", ")));

                    for (RegisteredListener registeredListener : registeredListeners) {
                        output.add(" - " + registeredListener.getPlugin().getName()
                                + ": " + registeredListener.getListener().getClass().getName()
                                + " at " + registeredListener.getPriority());
                    }
                }
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                output.add("Error with " + listenedClass.getSimpleName() + ": " + e.getClass().getName() + ": " + e.getMessage());
            }
            output.add("");
        }

        return String.join("\n", output);
    }

    private static String getPermissions() {
        List<String> output = new LinkedList<>();

        if (DiscordUtil.getJda() == null) {
            return "JDA == null";
        }

        Guild mainGuild = DiscordSRV.getPlugin().getMainGuild();
        if (mainGuild == null) {
            output.add("main guild -> null");
        } else {
            List<String> guildPermissions = new ArrayList<>();
            if (DiscordUtil.checkPermission(mainGuild, Permission.ADMINISTRATOR)) guildPermissions.add("administrator");
            if (DiscordUtil.checkPermission(mainGuild, Permission.MANAGE_ROLES)) guildPermissions.add("manage-roles");
            if (DiscordUtil.checkPermission(mainGuild, Permission.NICKNAME_MANAGE)) guildPermissions.add("nickname-manage");
            if (DiscordUtil.checkPermission(mainGuild, Permission.MANAGE_WEBHOOKS)) guildPermissions.add("manage-webhooks");
            output.add("main guild -> " + mainGuild + " [" + String.join(", ", guildPermissions) + "]");
        }

        VoiceChannel lobbyChannel = VoiceModule.getLobbyChannel();
        if (lobbyChannel == null) {
            output.add("voice lobby -> null");
        } else {
            List<String> channelPermissions = new ArrayList<>();
            if (DiscordUtil.checkPermission(lobbyChannel, Permission.VOICE_MOVE_OTHERS)) channelPermissions.add("move-members");
            output.add("voice lobby -> " + lobbyChannel + " [" + String.join(", ", channelPermissions) + "]");

            Category category = lobbyChannel.getParent();
            if (category == null) {
                output.add("voice category -> null");
            } else {
                List<String> categoryPermissions = new ArrayList<>();
                if (DiscordUtil.checkPermission(category, Permission.VOICE_MOVE_OTHERS)) categoryPermissions.add("move-members");
                if (DiscordUtil.checkPermission(category, Permission.MANAGE_CHANNEL)) categoryPermissions.add("manage-channel");
                if (DiscordUtil.checkPermission(category, Permission.MANAGE_PERMISSIONS)) categoryPermissions.add("manage-permissions");
                output.add("voice category -> " + category + " [" + String.join(", ", categoryPermissions) + "]");
            }
        }

        TextChannel consoleChannel = DiscordSRV.getPlugin().getConsoleChannel();
        if (consoleChannel == null) {
            output.add("console channel -> null");
        } else {
            List<String> consolePermissions = new ArrayList<>();
            if (DiscordUtil.checkPermission(consoleChannel, Permission.MESSAGE_READ)) consolePermissions.add("read");
            if (DiscordUtil.checkPermission(consoleChannel, Permission.MESSAGE_WRITE)) consolePermissions.add("write");
            if (DiscordUtil.checkPermission(consoleChannel, Permission.MANAGE_CHANNEL)) consolePermissions.add("channel-manage");
            output.add("console channel -> " + consoleChannel + " [" + String.join(", ", consolePermissions) + "]");
        }

        DiscordSRV.getPlugin().getChannels().forEach((channel, textChannelId) -> {
            TextChannel textChannel = StringUtils.isNotBlank(textChannelId) ? DiscordSRV.getPlugin().getJda().getTextChannelById(textChannelId) : null;
            if (textChannel != null) {
                List<String> outputForChannel = new LinkedList<>();
                if (DiscordUtil.checkPermission(textChannel, Permission.MESSAGE_READ)) outputForChannel.add("read");
                if (DiscordUtil.checkPermission(textChannel, Permission.MESSAGE_WRITE)) outputForChannel.add("write");
                if (DiscordUtil.checkPermission(textChannel, Permission.MANAGE_CHANNEL)) outputForChannel.add("channel-manage");
                if (DiscordUtil.checkPermission(textChannel, Permission.MESSAGE_MANAGE)) outputForChannel.add("message-manage");
                if (DiscordUtil.checkPermission(textChannel, Permission.MANAGE_WEBHOOKS)) outputForChannel.add("manage-webhooks");
                if (DiscordUtil.checkPermission(textChannel, Permission.MESSAGE_ADD_REACTION)) outputForChannel.add("add-reactions");
                if (DiscordUtil.checkPermission(textChannel, Permission.MESSAGE_HISTORY)) outputForChannel.add("history");
                if (DiscordUtil.checkPermission(textChannel, Permission.MESSAGE_ATTACH_FILES)) outputForChannel.add("attach-files");
                if (DiscordUtil.checkPermission(textChannel, Permission.MESSAGE_MENTION_EVERYONE)) outputForChannel.add("mention-everyone");
                if (DiscordUtil.checkPermission(textChannel, Permission.MESSAGE_EXT_EMOJI)) outputForChannel.add("external-emotes");
                output.add(channel + " -> " + textChannel + " [" + String.join(", ", outputForChannel) + "]");
            } else {
                output.add(channel + " -> null");
            }
        });

        return String.join("\n", output);
    }

    private static String getThreads() {
        Map<Thread, StackTraceElement[]> stackTraces = Thread.getAllStackTraces();
        Set<Thread> alreadyLoggedThreads = new HashSet<>();

        StringBuilder stringBuilder = new StringBuilder();
        for (Map.Entry<Thread, StackTraceElement[]> entry : stackTraces.entrySet()) {
            Thread thread = entry.getKey();
            String threadName = thread.getName();
            StackTraceElement[] traceElements = entry.getValue();
            if ((threadName.contains("DiscordSRV") || Arrays.stream(traceElements)
                    .anyMatch(trace -> trace.getClassName().startsWith("github.scarsz.discordsrv"))
            ) && alreadyLoggedThreads.add(thread)) {
                stringBuilder.append(threadName).append(":\n").append(PrettyUtil.beautify(traceElements)).append("\n");
            }
        }

        Thread serverThread = stackTraces.keySet().stream()
                .filter(thread -> thread.getName().equals("Server thread"))
                .findAny().orElse(null);
        if (serverThread != null && alreadyLoggedThreads.add(serverThread)) {
            stringBuilder.append("Server Thread:\n").append(PrettyUtil.beautify(serverThread.getStackTrace()));
        }

        stringBuilder.append("\nOther threads:\n");
        for (Thread thread : stackTraces.keySet()) {
            if (alreadyLoggedThreads.add(thread)) {
                Plugin plugin = null;
                try {
                    plugin = SchedulerUtil.isFolia()
                             ? null // not implemented on folia
                             : Bukkit.getScheduler().getActiveWorkers().stream()
                               .filter(work -> work.getThread() == thread)
                               .map(BukkitWorker::getOwner).findAny().orElse(null);
                } catch (Throwable ignored) {}

                stringBuilder.append("- ").append(thread.getName())
                        .append(plugin != null ? " (Owned by " + plugin.getName() + ")" : "")
                        .append('\n');
            }
        }

        if (SchedulerUtil.isFolia()) {
            stringBuilder.append("\nScheduler info is not available on Folia.");
            return stringBuilder.toString();
        }

        try {
            BukkitScheduler scheduler = Bukkit.getScheduler();
            Map<Plugin, AtomicInteger> scheduledTaskCounts = new HashMap<>();
            Map<Plugin, AtomicInteger> runningTaskCounts = new HashMap<>();

            for (BukkitTask task : scheduler.getPendingTasks()) {
                scheduledTaskCounts.computeIfAbsent(task.getOwner(), key -> new AtomicInteger()).incrementAndGet();
            }
            for (BukkitWorker activeWorker : scheduler.getActiveWorkers()) {
                runningTaskCounts.computeIfAbsent(activeWorker.getOwner(), key -> new AtomicInteger()).incrementAndGet();
            }

            stringBuilder.append("\nScheduled tasks:\n");
            scheduledTaskCounts.forEach((pl, in) -> stringBuilder.append(pl.getName()).append(": ").append(in.get()).append('\n'));

            stringBuilder.append("\nActive workers:\n");
            runningTaskCounts.forEach((pl, in) -> stringBuilder.append(pl.getName()).append(": ").append(in.get()).append('\n'));
        } catch (Throwable t) {
            stringBuilder.append("\nFailed to get scheduler information: ").append(t);
        }

        return stringBuilder.toString();
    }

    private static String getSystemInfo() {
        List<String> output = new LinkedList<>();

        // total number of processors or cores available to the JVM
        output.add("Available processors (cores): " + Runtime.getRuntime().availableProcessors());
        output.add("");

        // memory
        output.add("Free memory for JVM (MB): " + Runtime.getRuntime().freeMemory() / 1024 / 1024);
        output.add("Maximum memory for JVM (MB): " + (Runtime.getRuntime().maxMemory() == Long.MAX_VALUE ? "no limit" : Runtime.getRuntime().maxMemory() / 1024 / 1024));
        output.add("Total memory available for JVM (MB): " + Runtime.getRuntime().totalMemory() / 1024 / 1024);
        output.add("");

        // drive space
        File serverRoot = DiscordSRV.getPlugin().getDataFolder().getAbsoluteFile().getParentFile().getParentFile();
        output.add("Server storage:");
        output.add("- total space (MB): " + serverRoot.getTotalSpace() / 1024 / 1024);
        output.add("- free space (MB): " + serverRoot.getFreeSpace() / 1024 / 1024);
        output.add("- usable space (MB): " + serverRoot.getUsableSpace() / 1024 / 1024);
        output.add("");

        // java version
        Map<String, String> systemProperties = ManagementFactory.getRuntimeMXBean().getSystemProperties();
        output.add("Java version: " + systemProperties.get("java.version"));
        output.add("Java vendor: " + systemProperties.get("java.vendor") + " " + systemProperties.get("java.vendor.url"));
        output.add("Java home: " + systemProperties.get("java.home"));
        output.add("Command line: " + systemProperties.get("sun.java.command"));
        output.add("Time zone: " + systemProperties.get("user.timezone"));

        return String.join("\n", output);
    }

    /**
     * Upload the given file map to the current reporting service
     * @param files A Map representing a structure of file name & it's contents
     * @param requester Person who requested the debug report
     * @return A user-friendly message of how the report went
     */
    private static String uploadReport(List<Map<String, String>> files, int aesBits, String requester) {
        if (files.size() == 0) {
            return "ERROR/Failed to collect debug information: files list == 0... How???";
        }

        // Remove sensitive data and set the file content to "blank" if the file is blank
        files.forEach(map -> {
            String content = map.get("content");
            if (StringUtils.isNotBlank(content)) {
                // remove sensitive options from files
                for (String option : DebugUtil.SENSITIVE_OPTIONS) {
                    String value = DiscordSRV.config().getString(option);
                    if (StringUtils.isNotBlank(value) && !value.equalsIgnoreCase("username")) {
                        content = content.replace(value, "REDACTED");
                    }
                }

                // extra regex replace for bot tokens
                content = content.replaceAll("[A-Za-z\\d]{24}\\.[\\w-]{6}\\.[\\w-]{27}", "TOKEN REDACTED");
            } else {
                // put "blank" for null file contents
                content = "blank";
            }
            map.put("content", content);
        });

        final ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat("DiscordSRV - Debug Report Upload").build();
        final ExecutorService executor = Executors.newSingleThreadExecutor(threadFactory);
        try {
            return executor.invokeAny(Collections.singletonList(() -> {
                try {
                    String url = uploadToBin("https://bin.scarsz.me", aesBits, files, "Requested by " + requester);
                    DiscordSRV.api.callEvent(new DebugReportedEvent(requester, url));
                    return url;
                } catch (Exception e) {
                    throw e;
                }
            }), 20, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            DiscordSRV.error("Interrupted while uploading a debug report");
            return "ERROR/Interrupted while uploading the debug report";
        } catch (ExecutionException | TimeoutException e) {
            if (e instanceof ExecutionException && e.getCause().getMessage().toLowerCase().contains("illegal key size")) {
                return "ERROR/" + e.getCause().getMessage() + ". Try using /discordsrv debug 128";
            }

            File debugFolder = DiscordSRV.getPlugin().getDebugFolder();
            if (!debugFolder.exists()) debugFolder.mkdir();

            String debugName = "debug-" + System.currentTimeMillis() + ".zip";
            File zipFile = new File(debugFolder, debugName);

            try {
                ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(zipFile));
                for (Map<String, String> file : files) {
                    zipOutputStream.putNextEntry(new ZipEntry(file.get("name")));

                    byte[] data = file.get("content").getBytes();
                    zipOutputStream.write(data, 0, data.length);
                    zipOutputStream.closeEntry();
                }

                zipOutputStream.close();
            } catch (IOException ex) {
                DiscordSRV.error(ex);
                return "ERROR/Failed to upload to bin, and write to disk. (Unable to store debug report). Caused by "
                        + e.getCause().getMessage() + " and " + ex.getClass().getName() + ": " + ex.getMessage();
            }

            return "GENERATED TO FILE/Failed to upload to bin.scarsz.me, placed into plugins/DiscordSRV/debug/" + debugName
                    + ". Caused by " + (e instanceof ExecutionException ? e.getCause().getMessage() : e.getMessage());
        }
    }

    private static final Gson GSON = new Gson();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static String uploadToBin(String binHost, int aesBits, List<Map<String, String>> files, String description) {
        String key = RandomStringUtils.randomAlphanumeric(aesBits == 256 ? 32 : 16);
        byte[] keyBytes = key.getBytes();

        // decode to bytes, encrypt, base64
        List<Map<String, String>> encryptedFiles = new ArrayList<>();
        for (Map<String, String> file : files) {
            Map<String, String> encryptedFile = new HashMap<>(file);
            encryptedFile.entrySet().removeIf(entry -> StringUtils.isBlank(entry.getValue()));
            encryptedFile.replaceAll((k, v) -> b64(encrypt(keyBytes, file.get(k))));
            encryptedFiles.add(encryptedFile);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("description", b64(encrypt(keyBytes, description)));
        payload.put("expiration", TimeUnit.DAYS.toMinutes(21));
        payload.put("files", encryptedFiles);
        HttpRequest request = HttpRequest.post(binHost + "/v1/post")
                .userAgent("DiscordSRV " + DiscordSRV.version)
                .send(GSON.toJson(payload));
        if (request.code() == 200) {
            Map json = GSON.fromJson(request.body(), Map.class);
            if (json.get("status").equals("ok")) {
                return binHost + "/" + json.get("bin") + "#" + key;
            } else {
                String reason = "";
                if (json.containsKey("error")) {
                    Map error = (Map) json.get("error");
                    reason = ": " + error.get("type") + " " + error.get("message");
                }
                throw new RuntimeException("Bin upload status wasn't ok" + reason);
            }
        } else {
            throw new RuntimeException("Got bad HTTP status from Bin: " + request.code());
        }
    }

    public static String getStackTrace() {
        List<String> stackTrace = new LinkedList<>();
        stackTrace.add("Stack trace @ debug call (THIS IS NOT AN ERROR)");
        Arrays.stream(ExceptionUtils.getStackTrace(new Throwable()).split("\n"))
                .filter(s -> s.toLowerCase().contains("discordsrv"))
                .filter(s -> !s.contains("DebugUtil.getStackTrace"))
                .forEach(stackTrace::add);
        return String.join("\n", stackTrace);
    }

    public static String b64(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    /**
     * Encrypt the given `data` UTF-8 String with the given `key` (16 bytes, 128-bit)
     * @param key the key to encrypt data with
     * @param data the UTF-8 string to encrypt
     * @return the randomly generated IV + the encrypted data with no separator ([iv..., encryptedData...])
     */
    public static byte[] encrypt(byte[] key, String data) {
        return encrypt(key, data.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Encrypt the given `data` byte array with the given `key` (16 bytes, 128-bit)
     * @param key the key to encrypt data with
     * @param data the data to encrypt
     * @return the randomly generated IV + the encrypted data with no separator ([iv..., encryptedData...])
     */
    public static byte[] encrypt(byte[] key, byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            byte[] iv = new byte[cipher.getBlockSize()];
            RANDOM.nextBytes(iv);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            byte[] encrypted = cipher.doFinal(data);
            return ArrayUtils.addAll(iv, encrypted);
        } catch (InvalidKeyException e) {
            if (e.getMessage().toLowerCase().contains("illegal key size")) {
                throw new RuntimeException(e.getMessage(), e);
            } else {
                DiscordSRV.error(e);
            }
            return null;
        } catch (Exception ex) {
            DiscordSRV.error(ex);
            return null;
        }
    }

    public static class Message {

        private final Type type;
        private final String[] args;

        public Message(Type type, String... args) {
            this.type = type;
            this.args = args;
        }

        private boolean isWarning() {
            return type.warning;
        }

        @SuppressWarnings("RedundantCast") // it in fact isn't
        public String getMessage() {
            return String.format(type.message, (Object[]) args);
        }

        public String getTypeName() {
            return type.name();
        }

        public enum Type {

            // Warnings
            NO_CHAT_CHANNELS_LINKED(true, "No chat channels linked"),
            NO_CHANNELS_LINKED(true, "No channels linked (chat & console)"),
            SAME_CHANNEL_NAME(true, "Channel %s has the same in-game and Discord channel name"),
            UPDATE_CHECK_DISABLED(true, "Update checking is disabled"),

            // Errors
            RESPECT_CHAT_PLUGINS(false, "You have RespectChatPlugins set to false. This means DiscordSRV will completely ignore " +
                    "any other plugin's attempts to cancel a chat message from being broadcasted to the server. " +
                    "Disabling this is NOT a valid solution to your chat messages not being sent to Discord."
            ),
            PLUGIN_RELOADED(false, "Plugin has been initialized more than once (aka \"reloading\"). You will not receive support in this state."),
            INVALID_CHANNEL(false, "Invalid Channel %s (not found)"),
            NO_TOWNY_MAIN_CHANNEL(false, "No channel hooked to Towny's default channel: %s"),
            CONSOLE_AND_CHAT_SAME_CHANNEL(false, LangUtil.InternalMessage.CONSOLE_CHANNEL_ASSIGNED_TO_LINKED_CHANNEL.getDefinitions().get(Language.EN)),
            NOT_IN_ANY_SERVERS(false, LangUtil.InternalMessage.BOT_NOT_IN_ANY_SERVERS.getDefinitions().get(Language.EN)),
            NOT_CONNECTED(false, "Not connected to Discord!"),
            INVALID_BOT_TOKEN(false, "Invalid bot token, not connected to Discord."),
            DISALLOWED_INTENTS(false, "Disallowed intents (Make sure you followed all installation instructions), not connected to Discord."),
            DEBUG_MODE_NOT_ENABLED(false, "You do not have debug mode on. Run /discordsrv debugger, " +
                    "try to reproduce your problem and then run /discordsrv debugger upload to generate another report."
            ),
            UPDATE_AVAILABLE(false, "Update available. Download: https://get.discordsrv.com / https://snapshot.discordsrv.com"),
            LINKED_ROLE_GROUP_SYNC(false, "Cannot have the role in MinecraftDiscordAccountLinkedRoleNameToAddUserTo as a role in GroupRoleSynchronizationGroupsAndRolesToSync");

            private final boolean warning;
            private final String message;

            Type(boolean warning, String message) {
                this.warning = warning;
                this.message = message;
            }

        }

    }

}
