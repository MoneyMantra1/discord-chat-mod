package com.denisnumb.discord_chat_mod.commands.vanilla;

import com.denisnumb.discord_chat_mod.discord.chat_style.DiscordChatStyleProvider;
import com.denisnumb.discord_chat_mod.discord.chat_style.MessageType;
import com.denisnumb.discord_chat_mod.discord.model.ChannelCategory;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.dv8tion.jda.api.EmbedBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.SelectorContents;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.denisnumb.discord_chat_mod.MinecraftUtils.getPlayerListBySelector;
import static com.denisnumb.discord_chat_mod.discord.DiscordChannelRegistry.getAllContexts;
import static com.denisnumb.discord_chat_mod.discord.utils.DiscordMessageUtils.*;
import static com.denisnumb.discord_chat_mod.discord.chat_style.DiscordChatStyleProvider.buildPlayerParameters;
import static com.denisnumb.discord_chat_mod.discord.chat_style.DiscordChatStyleProvider.getDiscordMessageComponents;
import static com.denisnumb.discord_chat_mod.chat_style.Parameters.MESSAGE;

public class TellrawCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context){
        dispatcher.register(
                Commands.literal("tellraw")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("message", ComponentArgument.textComponent(context))
                                        .executes(ctx -> {
                                            Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "targets");
                                            Component message = ComponentArgument.getComponent(ctx, "message");

                                            if (ctx.getInput().split(" ", 3)[1].equals("@a")){
                                                handleDiscord(() -> {
                                                    String messageText = parseTellrawMessageForDiscord(ctx.getSource(), message);

                                                    if (trySendAfkStatusMessage(ctx.getSource(), messageText)) {
                                                        return;
                                                    }

                                                    getDiscordMessageComponents(
                                                            MessageType.TELLRAW_COMMAND,
                                                            Map.of(MESSAGE, replaceEmojiCodesToDiscordMentions(messageText)))
                                                            .ifPresent(components
                                                                    -> sendMessageFromServer(ChannelCategory.TELLRAW_COMMAND, getAllContexts(), components)
                                                    );
                                                });
                                            }

                                            for (ServerPlayer player : players)
                                                player.sendSystemMessage(ComponentUtils.updateForEntity(ctx.getSource(), message, player, 0), false);
                                            return players.size();
                                        })
                                )
                        )
        );
    }

    private static String parseTellrawMessageForDiscord(CommandSourceStack source, Component message) {
        StringBuilder messageTextBuilder = new StringBuilder();
        messageTextBuilder.append(parseComponentContents(source, message.getContents(), message.getStyle()));

        for (Component comp : message.getSiblings())
            messageTextBuilder.append(parseComponentContents(source, comp.getContents(), comp.getStyle()));

        return messageTextBuilder.toString();
    }

    private static String parseComponentContents(CommandSourceStack source, ComponentContents componentContents, Style style){
        try {
            Component component = componentContents.resolve(source, source.getEntity(), 0);

            if (componentContents instanceof SelectorContents selectorContents){
                List<ServerPlayer> playerList = getPlayerListBySelector(selectorContents.getPattern(), source);
                String result = component.getString();

                if (!playerList.isEmpty())
                    result = String.join(", ", playerList.stream().map(p -> p.getDisplayName().getString()).toList());
                else if (source.getEntity() instanceof ServerPlayer player && "@s".equals(selectorContents.getPattern()))
                    result = player.getDisplayName().getString();

                return applyStyles(style, result);
            }

            return applyStyles(style, component.getString());
        } catch (CommandSyntaxException e) {
            return "";
        }
    }

    private static boolean trySendAfkStatusMessage(CommandSourceStack source, String messageText) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return false;
        }

        String playerName = player.getDisplayName().getString();
        String normalized = messageText.strip();
        if (normalized.startsWith("[AFK] ")) {
            normalized = normalized.substring(6).strip();
        }

        AfkStatus afkStatus = null;
        if (normalized.equals(playerName + " is away")) {
            afkStatus = AfkStatus.AWAY;
        } else if (normalized.equals(playerName + " is back") || normalized.equals(playerName + " is back!")) {
            afkStatus = AfkStatus.BACK;
        } else if (normalized.equals(playerName + " has been kicked for inactivity")) {
            afkStatus = AfkStatus.KICKED;
        }

        if (afkStatus == null) {
            return false;
        }

        String authorText = switch (afkStatus) {
            case AWAY -> playerName + " is away";
            case BACK -> playerName + " is back";
            case KICKED -> playerName + " has been kicked for inactivity";
        };

        int color = switch (afkStatus) {
            case AWAY -> ChatFormatting.GOLD.getColor();
            case BACK -> ChatFormatting.GREEN.getColor();
            case KICKED -> ChatFormatting.RED.getColor();
        };

        DiscordChatStyleProvider.DiscordMessageComponents components = new DiscordChatStyleProvider.DiscordMessageComponents(
                Optional.empty(),
                Optional.of(new EmbedBuilder()
                        .setColor(color)
                        .setAuthor(authorText, null, buildPlayerParameters(player).get("{player_avatar_url}"))
                        .build())
        );

        sendMessageFromServer(ChannelCategory.PLAYER_JOIN_LEAVE, getAllContexts(), components);
        return true;
    }

    private enum AfkStatus {
        AWAY,
        BACK,
        KICKED
    }

    private static String applyStyles(Style style, String translatedText){
        if (style.isBold()) translatedText = "**" + translatedText + "**";
        if (style.isItalic()) translatedText = "*" + translatedText + "*";
        if (style.isStrikethrough()) translatedText = "~~" + translatedText + "~~";
        if (style.isUnderlined()) translatedText = "__" + translatedText + "__";
        if (style.isObfuscated()) translatedText = "||" + translatedText + "||";

        return translatedText;
    }
}
