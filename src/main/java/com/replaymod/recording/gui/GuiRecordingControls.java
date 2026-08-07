package com.replaymod.recording.gui;

import com.replaymod.core.ReplayMod;
import com.replaymod.core.utils.Utils;
import com.replaymod.core.versions.MCVer;
import com.replaymod.editor.gui.MarkerProcessor;
import com.replaymod.recording.packet.PacketListener;
import com.replaymod.replay.ScreenButtonExtension;
import de.johni0702.minecraft.gui.container.VanillaGuiScreen;
import de.johni0702.minecraft.gui.utils.EventRegistrations;
import de.johni0702.minecraft.gui.versions.callbacks.InitScreenCallback;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;

//#if MC>=11400
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
//#endif

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class GuiRecordingControls extends EventRegistrations {
    private static final String FANCYMENU_PAUSE_RESUME_ID = "replaymod_recording_pause_resume";
    private static final String FANCYMENU_START_STOP_ID = "replaymod_recording_start_stop";

    private final ReplayMod core;
    private final PacketListener packetListener;
    private boolean paused;
    private boolean stopped;

    //#if MC>=11400
    private ButtonWidget buttonPauseResume;
    private ButtonWidget buttonStartStop;
    //#endif

    public GuiRecordingControls(ReplayMod core, PacketListener packetListener, boolean autoStart) {
        this.core = core;
        this.packetListener = packetListener;

        paused = stopped = !autoStart;
    }

    //#if MC>=11400
    private void updateState() {
        if (buttonPauseResume != null) {
            buttonPauseResume.setMessage(Text.translatable("replaymod.gui.recording." + (paused ? "resume" : "pause")));
            buttonPauseResume.active = !stopped;
        }
        if (buttonStartStop != null) {
            buttonStartStop.setMessage(Text.translatable("replaymod.gui.recording." + (stopped ? "start" : "stop")));
        }
    }
    //#endif

    { on(InitScreenCallback.EVENT, this::injectIntoIngameMenu); }
    private void injectIntoIngameMenu(Screen guiScreen,
                                      //#if MC>=11400
                                      Collection<ClickableWidget> buttonList
                                      //#else
                                      //$$ Collection<net.minecraft.client.gui.GuiButton> buttonList
                                      //#endif
    ) {
        if (!(guiScreen instanceof GameMenuScreen)) {
            return;
        }
        if (buttonList.isEmpty()) {
            return; // menu-less pause (F3+Esc)
        }

        Function<Integer, Integer> yPos =
                MCVer.findButton(buttonList, "menu.returnToMenu", 1)
                        .map(Optional::of)
                        .orElse(MCVer.findButton(buttonList, "menu.disconnect", 1))
                        .<Function<Integer, Integer>>map(it -> (height) -> it.getY())
                        .orElse((height) -> height / 4 + 120 - 16);

        //#if MC>=11400
        VanillaGuiScreen vanillaGui = VanillaGuiScreen.wrap(guiScreen);
        int x = guiScreen.width / 2 - 100;
        int y = yPos.apply(guiScreen.height) + 16 + 8;

        buttonPauseResume = new RecordingButton(
                x,
                y,
                98,
                20,
                "replaymod.gui.recording." + (paused ? "resume" : "pause"),
                button -> {
                    if (Utils.ifMinimalModeDoPopup(vanillaGui, () -> {})) return;
                    if (paused) {
                        packetListener.addMarker(MarkerProcessor.MARKER_NAME_END_CUT);
                    } else {
                        packetListener.addMarker(MarkerProcessor.MARKER_NAME_START_CUT);
                    }
                    paused = !paused;
                    updateState();
                }
        );
        setFancyMenuWidgetIdentifier(buttonPauseResume, FANCYMENU_PAUSE_RESUME_ID);

        buttonStartStop = new RecordingButton(
                x + 102,
                y,
                98,
                20,
                "replaymod.gui.recording." + (stopped ? "start" : "stop"),
                button -> {
                    if (Utils.ifMinimalModeDoPopup(vanillaGui, () -> {})) return;
                    if (stopped) {
                        paused = false;
                        packetListener.addMarker(MarkerProcessor.MARKER_NAME_END_CUT);
                        core.printInfoToChat("replaymod.chat.recordingstarted");
                    } else {
                        int timestamp = (int) packetListener.getCurrentDuration();
                        if (!paused) {
                            packetListener.addMarker(MarkerProcessor.MARKER_NAME_START_CUT, timestamp);
                        }
                        packetListener.addMarker(MarkerProcessor.MARKER_NAME_SPLIT, timestamp + 1);
                    }
                    stopped = !stopped;
                    updateState();
                }
        );
        setFancyMenuWidgetIdentifier(buttonStartStop, FANCYMENU_START_STOP_ID);

        updateState();

        // The callback's buttonList is a read-only view on modern Forge and throws
        // UnsupportedOperationException when modified. Use ReplayMod's mutable Screen
        // adapter instead; it registers the widgets in drawables, selectables and children.
        List<ClickableWidget> mutableButtons = ((ScreenButtonExtension) guiScreen).replay_getButtons();
        mutableButtons.add(buttonPauseResume);
        mutableButtons.add(buttonStartStop);
        //#endif
    }

    //#if MC>=11400
    private static void setFancyMenuWidgetIdentifier(Object widget, String identifier) {
        Method method = findMethod(widget.getClass(), "setWidgetIdentifierFancyMenu");
        if (method == null) {
            return;
        }

        try {
            method.invoke(widget, identifier);
        } catch (ReflectiveOperationException ignored) {
            // FancyMenu is optional. ReplayMod must continue to work without it.
        }
    }

    private static Method findMethod(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(name)
                        && method.getParameterCount() == 1
                        && method.getParameterTypes()[0] == String.class) {
                    try {
                        method.setAccessible(true);
                    } catch (RuntimeException ignored) {
                        // Public mixin methods do not need this.
                    }
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static class RecordingButton extends ButtonWidget {
        private RecordingButton(int x, int y, int width, int height, String translationKey, PressAction onPress) {
            super(x, y, width, height, Text.translatable(translationKey), onPress, DEFAULT_NARRATION_SUPPLIER);
        }
    }
    //#endif

    public boolean isPaused() {
        return paused;
    }

    public boolean isStopped() {
        return stopped;
    }
}
