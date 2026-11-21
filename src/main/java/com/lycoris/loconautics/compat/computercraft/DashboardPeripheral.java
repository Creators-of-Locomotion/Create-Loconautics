package com.lycoris.loconautics.compat.computercraft;

import com.lycoris.loconautics.content.controls.panel.PanelBlockEntity;
import com.lycoris.loconautics.content.controls.panel.PanelPart;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.content.controls.panel.RedstoneLinkedPart;
import com.lycoris.loconautics.content.controls.panelbutton.ButtonPart;
import com.lycoris.loconautics.content.controls.panelgauge.GaugeBlockEntity;
import com.lycoris.loconautics.content.controls.panellever.LeverPart;
import com.lycoris.loconautics.content.controls.panelswitch.SwitchPart;
import com.lycoris.loconautics.content.controls.panelvalve.ValvePart;
import com.lycoris.loconautics.content.dashboard.DashboardBlock;
import com.lycoris.loconautics.content.dashboard.DashboardBlockEntity;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.NotAttachedException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.util.*;

/**
 * Exposes every part mounted on a dashboard run, so scripts can read the controls without going
 * through redstone link frequencies. The peripheral lives on one dashboard block but reports the
 * whole run it belongs to, ordered left to right as seen from the front.
 */
public class DashboardPeripheral implements IPeripheral, DashboardWatcher.Pollable {

    private static final Map<DashboardBlockEntity, DashboardPeripheral> CACHE = new WeakHashMap<>();

    private static final PanelSlot[] LEFT_TO_RIGHT = {PanelSlot.TOP_LEFT, PanelSlot.TOP_RIGHT};
    private final DashboardBlockEntity dashboard;
    private final Set<IComputerAccess> attached = new HashSet<>();
    private final Map<PartKey, Object> lastValues = new HashMap<>();

    private DashboardPeripheral(DashboardBlockEntity dashboard) {
        this.dashboard = dashboard;
    }

    public static DashboardPeripheral of(DashboardBlockEntity dashboard) {
        return CACHE.computeIfAbsent(dashboard, DashboardPeripheral::new);
    }

    private static Map<String, Object> describe(Control control, Level level) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("index", control.index());
        out.put("type", control.part().type().getSerializedName());
        out.put("slot", control.slot().getSerializedName());
        out.put("pos", pos(control.pos()));
        Component label = control.panel().label(control.slot());
        if (label != null) out.put("label", label.getString());
        if (control.part() instanceof GaugeBlockEntity.Gauge gauge) {
            out.put("metric", gauge.metric().getSerializedName());
        }
        Object value = value(control, level);
        if (value != null) out.put("value", value);
        out.put("writable", writable(control.part()));
        Integer signal = signal(control, level);
        if (signal != null) out.put("signal", signal);
        return out;
    }

    private static Object value(Control control, Level level) {
        PanelPart part = control.part();
        if (part instanceof LeverPart lever) return lever.power();
        if (part instanceof SwitchPart knob) return knob.on();
        if (part instanceof ButtonPart button) return button.pressed(level);
        if (part instanceof ValvePart valve) return (double) valve.degrees();
        if (part instanceof GaugeBlockEntity.Gauge gauge) {
            return (double) gauge.metric().normalized(source(control, gauge));
        }
        return null;
    }

    private static Integer signal(Control control, Level level) {
        if (!(control.part() instanceof RedstoneLinkedPart linked) || level == null) return null;
        return control.panel().outputSignal(control.slot(), linked.signal(level));
    }

    private static boolean writable(PanelPart part) {
        return part instanceof RedstoneLinkedPart;
    }

    private static net.minecraft.world.level.block.entity.BlockEntity source(Control control,
                                                                             GaugeBlockEntity.Gauge gauge) {
        return control.panel() instanceof GaugeBlockEntity panel ? panel.sourceOf(gauge) : null;
    }

    private static Map<String, Integer> pos(BlockPos pos) {
        Map<String, Integer> out = new LinkedHashMap<>();
        out.put("x", pos.getX());
        out.put("y", pos.getY());
        out.put("z", pos.getZ());
        return out;
    }

    @Override
    public @NonNull String getType() {
        return "loconautics:dashboard";
    }

    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof DashboardPeripheral p && p.dashboard == dashboard;
    }

    @Override
    public void attach(@NonNull IComputerAccess computer) {
        attached.add(computer);
        DashboardWatcher.watch(this);
    }

    @Override
    public void detach(@NonNull IComputerAccess computer) {
        attached.remove(computer);
        if (!attached.isEmpty()) return;
        DashboardWatcher.unwatch(this);
        lastValues.clear();
    }

    /**
     * Fires a {@code part_changed} event when discrete parts change
     * (may not be the best method?)
     */
    @Override
    public void pollDashboard() {
        if (attached.isEmpty()) {
            DashboardWatcher.unwatch(this);
            return;
        }
        if (dashboard.isRemoved() || dashboard.getLevel() == null) {
            DashboardWatcher.unwatch(this);
            attached.clear();
            lastValues.clear();
            return;
        }
        Level level = dashboard.getLevel();
        List<Control> controls;
        try {
            controls = controls();
        } catch (LuaException gone) {
            lastValues.clear();
            return;
        }
        Set<PartKey> seen = new HashSet<>();
        for (Control control : controls) {
            if (!writable(control.part())) continue;
            Object value = value(control, level);
            if (value == null) continue;
            PartKey key = new PartKey(control.pos(), control.slot());
            seen.add(key);
            Object before = lastValues.put(key, value);
            if (before == null || before.equals(value)) continue;
            queue("part_changed", control.index(), control.part().type().getSerializedName(), value);
        }
        lastValues.keySet().retainAll(seen);
    }

    private void queue(String event, Object... arguments) {
        for (IComputerAccess computer : List.copyOf(attached)) {
            try {
                computer.queueEvent(event, arguments);
            } catch (NotAttachedException gone) {
                attached.remove(computer);
            }
        }
    }

    private DashboardBlockEntity live() throws LuaException {
        if (dashboard.isRemoved() || dashboard.getLevel() == null) {
            throw new LuaException("the dashboard is gone, it was broken or unloaded");
        }
        return dashboard;
    }

    private Level level() throws LuaException {
        return live().getLevel();
    }

    private List<Control> controls() throws LuaException {
        Level level = level();
        List<BlockPos> run = DashboardBlock.runPositions(level, dashboard.getBlockPos());
        List<Control> controls = new ArrayList<>();
        for (int i = run.size() - 1; i >= 0; i--) {
            BlockPos pos = run.get(i);
            if (!(level.getBlockEntity(pos) instanceof PanelBlockEntity panel)) continue;
            for (PanelSlot slot : LEFT_TO_RIGHT) {
                PanelPart part = panel.getPart(slot);
                if (part != null) controls.add(new Control(controls.size() + 1, pos, panel, slot, part));
            }
        }
        return controls;
    }

    private Control control(int index) throws LuaException {
        List<Control> controls = controls();
        if (index < 1 || index > controls.size()) {
            // hint in case lol
            throw new LuaException(index < 1
                    ? "parts are numbered from 1"
                    : "there is no part " + index + ", this run has " + controls.size());
        }
        return controls.get(index - 1);
    }

    @LuaFunction(mainThread = true)
    public final int getDashboardCount() throws LuaException {
        return DashboardBlock.runPositions(level(), dashboard.getBlockPos()).size();
    }

    @LuaFunction(mainThread = true)
    public final int getPartCount() throws LuaException {
        return controls().size();
    }

    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> getParts() throws LuaException {
        Level level = level();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Control control : controls()) out.add(describe(control, level));
        return out;
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> getPart(int index) throws LuaException {
        return describe(control(index), level());
    }

    @LuaFunction(mainThread = true)
    public final String getPartType(int index) throws LuaException {
        return control(index).part().type().getSerializedName();
    }

    @LuaFunction(mainThread = true)
    public final Object getValue(int index) throws LuaException {
        return value(control(index), level());
    }

    @LuaFunction(mainThread = true)
    public final boolean setValue(int index, Object value) throws LuaException {
        Control control = control(index);
        PanelPart part = control.part();
        boolean on;
        int strength;
        if (value instanceof Boolean pressed) {
            on = pressed;
            strength = pressed ? RedstoneLinkedPart.MAX_SIGNAL : 0;
        } else if (value instanceof Number number) {
            double amount = number.doubleValue();
            on = amount > 0;
            strength = (int) Math.round(amount);
        } else {
            throw new LuaException("part " + index + " takes a number or a boolean");
        }
        if (part instanceof LeverPart lever) {
            int power = Mth.clamp(strength, 0, LeverPart.MAX_POWER);
            control.panel().replacePart(control.slot(), new LeverPart(power, lever.link(), lever.body(),
                    lever.knobColor(), lever.style(), lever.options()));
            return true;
        }
        if (part instanceof SwitchPart knob) {
            int power = on ? SwitchPart.MAX_POWER : 0;
            control.panel().replacePart(control.slot(), new SwitchPart(power, knob.link(), knob.body(),
                    knob.knobColor(), knob.style(), knob.options()));
            return true;
        }
        // does it make sense to trigger button from lua...?
        if (part instanceof ButtonPart button) {
            if (!on) return false;
            try (Level level = level()) {
                control.panel().replacePart(control.slot(), new ButtonPart(
                        level.getGameTime() + ButtonPart.PRESS_TICKS, button.link(), button.body(),
                        button.capColor(), button.style(), button.options()));
                level.playSound(null, control.pos(), SoundEvents.STONE_BUTTON_CLICK_ON,
                        SoundSource.BLOCKS, 0.35f, 1.4f);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return true;
        }
        return false;
    }

    @LuaFunction(mainThread = true)
    public final String getLabel(int index) throws LuaException {
        Control control = control(index);
        Component label = control.panel().label(control.slot());
        return label == null ? null : label.getString();
    }

    @LuaFunction(mainThread = true)
    public final Integer getSignal(int index) throws LuaException {
        return signal(control(index), level());
    }

    @LuaFunction(mainThread = true)
    public final String getText(int index) throws LuaException {
        Control control = control(index);
        if (!(control.part() instanceof GaugeBlockEntity.Gauge gauge)) return null;
        return gauge.metric().read(source(control, gauge));
    }

    private record Control(int index, BlockPos pos, PanelBlockEntity panel, PanelSlot slot, PanelPart part) {
    }

    private record PartKey(BlockPos pos, PanelSlot slot) {
    }

}
