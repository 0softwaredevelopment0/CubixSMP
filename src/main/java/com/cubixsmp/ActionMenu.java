package com.cubixsmp;

import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.common.ClientboundClearDialogPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Нативное диалоговое окно (Minecraft 26.2 Dialog API) «Что вы хотите сделать?»,
 * открываемое кликом по НИКУ отправителя в чате (команда {@code /csmp action <ник>}).
 * <p>
 * Так же, как в UltimateImprovments:
 * <ul>
 *   <li>{@link MultiActionDialog} — окно с заголовком, текстом и кнопками
 *       [ЛС] / [TPA] / [Отмена] (CustomAll-действия).</li>
 *   <li>Кнопка [ЛС] — закрывает это окно и открывает второе окно с полем ввода
 *       ({@link TextInput}) для текста сообщения, кнопки [✔ Отправить] / [✖ Отмена].</li>
 *   <li>Кнопка [TPA] — закрывает окно и сразу выполняет tpa-command.</li>
 *   <li>Кнопки [Отмена] — просто закрывают окно.</li>
 * </ul>
 * <p>
 * ВАЖНО про «Waiting for server…»: оба диалога используют {@link DialogAction#CLOSE}
 * (а НЕ {@link DialogAction#WAIT_FOR_RESPONSE}). При {@code WAIT_FOR_RESPONSE} клиент
 * после клика уходит на отдельный экран {@code WaitingForResponseScreen} («Waiting
 * for server…») и игнорирует {@link ClientboundClearDialogPacket} (клиентский
 * {@code clearDialog()} закрывает только {@code DialogScreen}), поэтому окно висело бы
 * ~4 секунды. С {@code CLOSE} клиент закрывает окно СРАЗУ после клика сам
 * (пакет клика с вводом при этом всё равно отправляется), а сервер лишь выполняет
 * действие. {@link ClientboundClearDialogPacket} отправляется дополнительно как
 * страховка — на игровом экране он безвреден.
 */
public class ActionMenu implements Listener {

    private static final String NS = "cubixsmp";

    /** Идентификаторы CustomAll-действий кнопок. */
    private static final Identifier ACTION_MSG = Identifier.fromNamespaceAndPath(NS, "action_msg");
    private static final Identifier ACTION_TPA = Identifier.fromNamespaceAndPath(NS, "action_tpa");
    private static final Identifier ACTION_CANCEL = Identifier.fromNamespaceAndPath(NS, "action_cancel");
    private static final Identifier MSG_SEND = Identifier.fromNamespaceAndPath(NS, "msg_send");
    private static final Identifier MSG_CANCEL = Identifier.fromNamespaceAndPath(NS, "msg_cancel");

    /** Ключи для сравнения в {@link PlayerCustomClickEvent}. */
    private static final Key K_MSG = Key.key(NS, "action_msg");
    private static final Key K_TPA = Key.key(NS, "action_tpa");
    private static final Key K_CANCEL = Key.key(NS, "action_cancel");
    private static final Key K_MSG_SEND = Key.key(NS, "msg_send");
    private static final Key K_MSG_CANCEL = Key.key(NS, "msg_cancel");

    private static final String CFG = "chat-format.action-dialog.";

    private final CubixSMP plugin;
    /** Открытые диалоги: UUID игрока → имя целевого игрока. Перезаписывается при каждом открытии. */
    private final Map<UUID, String> openDialogs = new HashMap<>();

    public ActionMenu(CubixSMP plugin) {
        this.plugin = plugin;
    }

    /**
     * Открывает диалог действий «Что вы хотите сделать?» над игроком {@code target}.
     */
    public void open(Player player, Player target) {
        if (!(player instanceof CraftPlayer craftPlayer)) return;
        ServerPlayer serverPlayer = craftPlayer.getHandle();
        String targetName = target.getName();

        net.minecraft.network.chat.Component title = nativeText(
                plugin.getConfig().getString(CFG + "title", "<gold>✦ Что вы хотите сделать?</gold>"));
        net.minecraft.network.chat.Component externalTitle = nativeText("<gray>Действия с игроком</gray>");
        net.minecraft.network.chat.Component body = nativeText(plugin.getConfig()
                .getString(CFG + "body", "<white>Действия с игроком </white><yellow>{player}</yellow><white>:</white>")
                .replace("{player}", targetName));

        ActionButton msgButton = new ActionButton(
                new CommonButtonData(nativeText(plugin.getConfig()
                        .getString(CFG + "msg-button", "<green>✉ Личное сообщение</green>")), 200),
                Optional.of(new CustomAll(ACTION_MSG, Optional.empty())));
        ActionButton tpaButton = new ActionButton(
                new CommonButtonData(nativeText(plugin.getConfig()
                        .getString(CFG + "tpa-button", "<aqua>⛏ Телепортация</aqua>")), 200),
                Optional.of(new CustomAll(ACTION_TPA, Optional.empty())));
        ActionButton cancelButton = new ActionButton(
                new CommonButtonData(nativeText(plugin.getConfig()
                        .getString(CFG + "cancel-button", "<red>✖ Отмена</red>")), 200),
                Optional.of(new CustomAll(ACTION_CANCEL, Optional.empty())));

        CommonDialogData data = new CommonDialogData(
                title,
                Optional.of(externalTitle),
                true,                             // canCloseWithEscape
                true,                             // pause
                DialogAction.CLOSE,                // клиент закрывает окно сам, мгновенно (без «Waiting for server»)
                List.of(new PlainMessage(body, 310)),
                List.of());                       // без полей ввода

        MultiActionDialog dialog = new MultiActionDialog(
                data,
                List.of(msgButton, tpaButton),
                Optional.of(cancelButton),
                2);

        openDialogs.put(player.getUniqueId(), targetName);
        serverPlayer.openDialog(Holder.direct(dialog));
    }

    /**
     * Открывает диалог ввода текста сообщения для /m (после выбора «ЛС»).
     */
    private void openMessageDialog(Player player, String targetName) {
        if (!(player instanceof CraftPlayer craftPlayer)) return;
        ServerPlayer serverPlayer = craftPlayer.getHandle();

        net.minecraft.network.chat.Component title = nativeText(plugin.getConfig()
                .getString(CFG + "msg-title", "<gold>✉ Сообщение для </gold><yellow>{player}</yellow>")
                .replace("{player}", targetName));
        net.minecraft.network.chat.Component externalTitle = nativeText("<gray>Личное сообщение</gray>");
        net.minecraft.network.chat.Component body = nativeText(plugin.getConfig()
                .getString(CFG + "msg-body", "<white>Введите текст сообщения для </white><yellow>{player}</yellow><white>:</white>")
                .replace("{player}", targetName));

        TextInput input = new TextInput(
                300,
                nativeText(plugin.getConfig().getString(CFG + "msg-input-label", "<gray>Текст сообщения</gray>")),
                false,
                "",
                256,
                Optional.empty());

        ActionButton sendButton = new ActionButton(
                new CommonButtonData(nativeText(plugin.getConfig()
                        .getString(CFG + "msg-send-button", "<green>✔ Отправить</green>")), 200),
                Optional.of(new CustomAll(MSG_SEND, Optional.empty())));
        ActionButton cancelButton = new ActionButton(
                new CommonButtonData(nativeText(plugin.getConfig()
                        .getString(CFG + "msg-cancel-button", "<red>✖ Отмена</red>")), 200),
                Optional.of(new CustomAll(MSG_CANCEL, Optional.empty())));

        CommonDialogData data = new CommonDialogData(
                title,
                Optional.of(externalTitle),
                true,
                true,
                DialogAction.CLOSE,                // клиент закрывает окно сам, мгновенно (без «Waiting for server»)
                List.of(new PlainMessage(body, 310)),
                List.of(new Input("message", input)));

        MultiActionDialog dialog = new MultiActionDialog(
                data,
                List.of(sendButton),
                Optional.of(cancelButton),
                1);

        serverPlayer.openDialog(Holder.direct(dialog));
    }

    /**
     * Обработчик кликов по кнопкам нативных диалогов (PlayerCustomClickEvent).
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onCustomClick(PlayerCustomClickEvent event) {
        Key identifier = event.getIdentifier();
        Player player = getPlayer(event);
        if (player == null) return;

        // ─── Отмена (в обоих диалогах) — просто закрываем ───
        if (identifier.equals(K_CANCEL) || identifier.equals(K_MSG_CANCEL)) {
            close(player);
            openDialogs.remove(player.getUniqueId());
            return;
        }

        // ─── ЛС — закрываем и открываем диалог ввода сообщения ───
        if (identifier.equals(K_MSG)) {
            close(player);
            String targetName = openDialogs.get(player.getUniqueId());
            if (targetName == null) return;
            // Открываем новое окно на 1 тик позже — клиент уже закрыл предыдущее сам
            // (DialogAction.CLOSE), окно ввода появляется чисто, без экрана ожидания
            String finalTarget = targetName;
            plugin.getServer().getScheduler().runTask(plugin, () -> openMessageDialog(player, finalTarget));
            return;
        }

        // ─── TPA — закрываем и выполняем команду сразу ───
        if (identifier.equals(K_TPA)) {
            close(player);
            String targetName = openDialogs.remove(player.getUniqueId());
            if (targetName == null) return;
            String cmd = plugin.getConfig().getString("chat-format.tpa-command", "/tpa {player}")
                    .replace("{player}", targetName);
            if (cmd.startsWith("/")) cmd = cmd.substring(1);
            player.performCommand(cmd);
            return;
        }

        // ─── Отправка /m — читаем текст из поля ввода и выполняем команду ───
        if (identifier.equals(K_MSG_SEND)) {
            String targetName = openDialogs.remove(player.getUniqueId());
            DialogResponseView response = event.getDialogResponseView();
            String text = response == null ? null : response.getText("message");
            close(player);
            if (targetName == null || text == null || text.trim().isEmpty()) {
                player.sendMessage(MessagesManager.getString(
                        "command.action_empty_message", "§c❌ Сообщение не может быть пустым!"));
                return;
            }
            String cmd = plugin.getConfig().getString("chat-format.msg-command", "/m {player}")
                    .replace("{player}", targetName) + " " + text.trim();
            if (cmd.startsWith("/")) cmd = cmd.substring(1);
            player.performCommand(cmd);
        }
    }

    /**
     * Закрывает открытый у игрока диалог (страховка). Основной механизм закрытия —
     * {@link DialogAction#CLOSE}: клиент закрывает окно сам сразу после клика,
     * поэтому «Waiting for server…» не возникает. Этот пакет отправляется для
     * синхронизации состояния на случай, если клиент остался на экране диалога.
     */
    public void close(Player player) {
        if (!(player instanceof CraftPlayer craftPlayer)) return;
        if (craftPlayer.getHandle().connection == null) return;
        craftPlayer.getHandle().connection.send(ClientboundClearDialogPacket.INSTANCE);
    }

    private static Player getPlayer(PlayerCustomClickEvent event) {
        if (event.getCommonConnection() instanceof PlayerGameConnection gameConnection) {
            return gameConnection.getPlayer();
        }
        return null;
    }

    /** MiniMessage-строка → нативный Minecraft Component (как в UltimateImprovments). */
    private static net.minecraft.network.chat.Component nativeText(String miniMessage) {
        Component parsed = MiniMessage.miniMessage().deserialize(miniMessage);
        String legacy = LegacyComponentSerializer.legacySection().serialize(parsed);
        return net.minecraft.network.chat.Component.literal(legacy);
    }
}
