package com.coobird.staticlogistics.client.data;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.logistics.node.ChannelConfigurationEdit;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/// 配置编辑会话：统一管理请求边界、数值合并、回执和关闭。
/// 不依赖界面控件或网络实现；输入框草稿与目录展示不属于会话状态。
public final class ConfiguratorEditSession {
    public record Focus(ConnectionKey connection, LogisticsNode node) {
    }

    public record Target(LogisticsNode node, boolean input) {
    }

    public record NumberSubmission(LogisticsNode node, ChannelConfigurationEdit.NumberEdit edit, long requestId) {
    }

    public enum Completion {IGNORED, NUMBER, REQUEST}

    private sealed interface State permits Ready, Waiting, Closed {
    }

    private record Ready() implements State {
    }

    private record Waiting(long requestId, long started, Target intent) implements State {
    }

    private record Closed(Target intent) implements State {
    }

    private record NumberState(LogisticsNode node, long value, long revision, long sentRevision, long requestId,
                               long sentAt) {
    }

    private static final AtomicLong REQUEST_IDS = new AtomicLong();
    private static final long TIMEOUT = 10_000_000_000L;
    private final EnumMap<ChannelConfigurationEdit.NumberField, NumberState> numbers = new EnumMap<>(ChannelConfigurationEdit.NumberField.class);
    private final Consumer<NumberSubmission> sendNumber;
    private final LongSupplier clock;
    private State state = new Ready();
    private Runnable navigation;
    private Focus focus;
    private final List<ChannelConfigurationEdit> batchDraft = new ArrayList<>();

    public List<ChannelConfigurationEdit> batchEdits() {
        return List.copyOf(batchDraft);
    }

    public boolean hasBatchDraft() {
        return !batchDraft.isEmpty();
    }

    public void clearBatchDraft() {
        batchDraft.clear();
    }

    /// 草稿只覆盖玩家实际修改的字段，不复制主选节点的整份配置。
    public void stageBatch(ChannelConfigurationEdit edit) {
        if (!canEdit()) return;
        if (edit instanceof ChannelConfigurationEdit.TypePatchEdit(
            List<ResourceLocation> added2,
            List<ResourceLocation> removed2
        )) {
            var added = new LinkedHashSet<>(added2);
            var removed = new LinkedHashSet<>(removed2);
            for (var previous : batchDraft)
                if (previous instanceof ChannelConfigurationEdit.TypePatchEdit(
                    List<ResourceLocation> added1,
                    List<ResourceLocation> removed1
                )) {
                    for (var id : added1) if (!removed.contains(id)) added.add(id);
                    for (var id : removed1) if (!added.contains(id)) removed.add(id);
                }
            edit = new ChannelConfigurationEdit.TypePatchEdit(List.copyOf(added), List.copyOf(removed));
        }
        ChannelConfigurationEdit replacement = edit;
        batchDraft.removeIf(previous -> sameField(previous, replacement));
        batchDraft.add(edit);
    }

    @SuppressWarnings("unchecked")
    public <T extends ChannelConfigurationEdit> T preview(T original) {
        for (var edit : batchDraft) if (sameField(edit, original)) return (T) edit;
        return original;
    }

    private static boolean sameField(ChannelConfigurationEdit first, ChannelConfigurationEdit second) {
        if (first.getClass() != second.getClass()) return false;
        if (first instanceof ChannelConfigurationEdit.NumberEdit a && second instanceof ChannelConfigurationEdit.NumberEdit b)
            return a.field() == b.field();
        if (first instanceof ChannelConfigurationEdit.BooleanEdit a && second instanceof ChannelConfigurationEdit.BooleanEdit b)
            return a.field() == b.field();
        return true;
    }

    private long focusStarted;

    public ConfiguratorEditSession(Consumer<NumberSubmission> sendNumber) {
        this(sendNumber, System::nanoTime);
    }

    public ConfiguratorEditSession(Consumer<NumberSubmission> sendNumber, LongSupplier clock) {
        this.sendNumber = sendNumber;
        this.clock = clock;
    }

    public boolean canEdit() {
        return state instanceof Ready;
    }

    public boolean awaiting() {
        return state instanceof Waiting;
    }

    public Target intendedTarget() {
        return state instanceof Waiting waiting ? waiting.intent() : state instanceof Closed(
            Target intent
        ) ? intent : null;
    }

    public boolean acceptsTarget(long requestId, LogisticsNode node) {
        return state instanceof Waiting waiting && waiting.requestId() == requestId && (waiting.intent() == null || waiting.intent().node().equals(node));
    }

    public Focus pendingFocus() {
        return focus;
    }

    /// 自动校正只能在选择会话空闲时运行，不能替换玩家导航。
    public boolean canReconcileSelection() {
        return canEdit() && navigation == null && focus == null;
    }

    public void focusChannel(ConnectionKey connection, LogisticsNode node) {
        if (!canEdit()) return;
        focus = new Focus(connection, node);
        focusStarted = clock.getAsLong();
    }

    /// 创建结果只属于发起它的请求；后续玩家导航优先。
    public void acceptCreatedChannel(long requestId, ConnectionKey connection, LogisticsNode node) {
        if (!(state instanceof Waiting waiting) || waiting.requestId() != requestId || navigation != null) return;
        focus = new Focus(connection, node);
        focusStarted = clock.getAsLong();
    }

    public void advanceFocus(Predicate<Focus> available, Consumer<Focus> select) {
        if (!canEdit() || focus == null) return;
        Focus next = focus;
        if (available.test(next)) {
            focus = null;
            select.accept(next);
        } else if (clock.getAsLong() - focusStarted > TIMEOUT) {
            focus = null;
        }
    }

    public void queueNumber(LogisticsNode node, ChannelConfigurationEdit.NumberEdit edit) {
        if (!canEdit()) throw new IllegalStateException("Cannot edit during a configurator transition");
        NumberState previous = numbers.get(edit.field());
        boolean same = previous != null && previous.node().equals(node);
        /// 切换目标必须通过 begin()；因此旧目标的未发送值不能被新目标覆盖。
        if (!same && previous != null && previous.revision() != previous.sentRevision()) flush();
        numbers.put(edit.field(), new NumberState(node, edit.value(), same ? previous.revision() + 1 : 1, same ? previous.sentRevision() : 0, same ? previous.requestId() : 0, same ? previous.sentAt() : 0));
    }

    public Long pendingValue(LogisticsNode node, ChannelConfigurationEdit.NumberField field) {
        NumberState value = numbers.get(field);
        return value != null && value.node().equals(node) ? value.value() : null;
    }

    public void flush() {
        if (state instanceof Closed) return;
        for (var entry : numbers.entrySet()) {
            NumberState value = entry.getValue();
            if (value.revision() == value.sentRevision()) continue;
            long id = REQUEST_IDS.incrementAndGet();
            sendNumber.accept(new NumberSubmission(value.node(), new ChannelConfigurationEdit.NumberEdit(entry.getKey(), value.value()), id));
            entry.setValue(new NumberState(value.node(), value.value(), value.revision(), value.revision(), id, clock.getAsLong()));
        }
    }

    public long begin(Target intent) {
        if (!canEdit()) throw new IllegalStateException("A configurator transition is already in progress");
        flush();
        long id = REQUEST_IDS.incrementAndGet();
        focus = null;
        state = new Waiting(id, clock.getAsLong(), intent);
        return id;
    }

    public Completion acknowledge(long requestId) {
        if (state instanceof Closed) return Completion.IGNORED;
        if (state instanceof Waiting waiting && waiting.requestId() == requestId) {
            state = new Ready();
            return Completion.REQUEST;
        }
        var iterator = numbers.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            NumberState value = entry.getValue();
            if (value.requestId() == 0 || value.requestId() != requestId) continue;
            if (value.revision() == value.sentRevision()) iterator.remove();
            else
                entry.setValue(new NumberState(value.node(), value.value(), value.revision(), value.sentRevision(), 0, 0));
            return Completion.NUMBER;
        }
        return Completion.IGNORED;
    }

    /// 等待结构性请求时只保留最后一次导航意图，数值提交不阻塞导航。
    public boolean deferNavigation(Runnable action) {
        if (state instanceof Closed) return true;
        focus = null;
        if (!awaiting()) return false;
        navigation = action;
        return true;
    }

    public void resumeNavigation() {
        if (!canEdit()) return;
        Runnable action = navigation;
        navigation = null;
        if (action != null) action.run();
    }

    public boolean expired() {
        long now = clock.getAsLong();
        if (state instanceof Waiting waiting && now - waiting.started() > TIMEOUT) return true;
        return numbers.values().stream().anyMatch(value -> value.requestId() != 0 && now - value.sentAt() > TIMEOUT);
    }

    /// 关闭包发出前先发送最后的数值，不等待界面销毁后再继续排队。
    public void close() {
        flush();
        navigation = null;
        numbers.clear();
        state = new Closed(intendedTarget());
    }
}
