package com.robsterad.infiniteinv.network;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

@Environment(EnvType.CLIENT)
public final class ClientPackets {
    private ClientPackets() {}

    public static void send(InventoryPacket packet) {
        ClientPlayNetworking.send(packet.getId(), packet.toBuffer());
    }
}

