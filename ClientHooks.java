package com.morphmod.client;

import net.minecraft.client.Minecraft;

/** Ponte client-only usada pelo item (via DistExecutor) para nao carregar classes de GUI no servidor. */
public final class ClientHooks {
    private ClientHooks() {}

    public static void openConsole() {
        Minecraft.getInstance().setScreen(new ConsoleScreen());
    }
}
