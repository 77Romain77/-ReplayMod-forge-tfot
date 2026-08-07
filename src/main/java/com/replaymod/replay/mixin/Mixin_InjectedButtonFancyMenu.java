package com.replaymod.replay.mixin;

import com.replaymod.replay.handler.GuiHandler;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

/**
 * Gives ReplayMod's vanilla widgets stable identifiers understood by FancyMenu.
 *
 * FancyMenu adds setWidgetIdentifierFancyMenu(String) to Minecraft widgets at
 * runtime. Reflection keeps FancyMenu entirely optional and avoids a hard
 * compile-time dependency on it.
 */
@Mixin(GuiHandler.InjectedButton.class)
public abstract class Mixin_InjectedButtonFancyMenu {
    @Unique
    private static final int REPLAYMOD_BUTTON_REPLAY_VIEWER = 17890234;
    @Unique
    private static final int REPLAYMOD_BUTTON_EXIT_REPLAY = 17890235;
    @Unique
    private static final int REPLAYMOD_EXIT_REPLAY_WIDTH = 98;
    @Unique
    private static final int REPLAYMOD_EXIT_REPLAY_RIGHT_MARGIN = 20;

    @Shadow
    @Final
    public Screen guiScreen;

    @Shadow
    @Final
    public int id;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void replaymod$registerFancyMenuIdentifier(CallbackInfo ci) {
        String identifier;
        if (id == REPLAYMOD_BUTTON_REPLAY_VIEWER) {
            identifier = "replaymod_replay_viewer";
        } else if (id == REPLAYMOD_BUTTON_EXIT_REPLAY) {
            replaymod$applyExitReplayDefaultLayout();
            identifier = "replaymod_exit_replay";
        } else {
            return;
        }

        replaymod$setFancyMenuIdentifier(this, identifier);
    }

    @Unique
    private void replaymod$applyExitReplayDefaultLayout() {
        ClickableWidget widget = (ClickableWidget) (Object) this;
        widget.setWidth(REPLAYMOD_EXIT_REPLAY_WIDTH);
        widget.setX(Math.max(0, guiScreen.width - REPLAYMOD_EXIT_REPLAY_RIGHT_MARGIN - REPLAYMOD_EXIT_REPLAY_WIDTH));
        widget.setY(Math.max(0, guiScreen.height / 2 - widget.getHeight() / 2));
    }

    @Unique
    private static void replaymod$setFancyMenuIdentifier(Object widget, String identifier) {
        Method method = replaymod$findMethod(widget.getClass(), "setWidgetIdentifierFancyMenu");
        if (method == null) {
            return;
        }

        try {
            method.invoke(widget, identifier);
        } catch (ReflectiveOperationException ignored) {
            // FancyMenu is optional. Its absence must not affect ReplayMod.
        }
    }

    @Unique
    private static Method replaymod$findMethod(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == 1
                        && method.getParameterTypes()[0] == String.class) {
                    try {
                        method.setAccessible(true);
                    } catch (RuntimeException ignored) {
                        // Public mixin methods do not require this, so keep going.
                    }
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }
}
