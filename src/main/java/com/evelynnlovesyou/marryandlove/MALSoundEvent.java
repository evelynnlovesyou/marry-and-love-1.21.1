package com.evelynnlovesyou.marryandlove;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public class MALSoundEvent {

    public static final Identifier KISS_ID = new Identifier("marryandlove", "kiss");
    public static final SoundEvent KISS = SoundEvent.of(KISS_ID);

    public static void registerSounds() {
        Registry.register(Registries.SOUND_EVENT, KISS_ID, KISS);
    }
}
