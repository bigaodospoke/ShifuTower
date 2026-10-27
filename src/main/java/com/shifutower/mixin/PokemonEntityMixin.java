package com.shifutower.mixin;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.shifutower.ShifuTower;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Na torre todo Pokémon que desmaia é uma cópia da batalha, sem dono, que o Cobblemon trata como selvagem.
 * Aqui corta o drop "de vanilla" (equipamento e XP); o item segurado e os drops da espécie são cortados em
 * {@link PokemonServerDelegateMixin}.
 */
@Mixin(PokemonEntity.class)
public abstract class PokemonEntityMixin {
    @Inject(method = "dropAllDeathLoot", at = @At("HEAD"), cancellable = true)
    private void shifutower$semDropsNaTorre(ServerLevel level, DamageSource source, CallbackInfo ci) {
        if (level.dimension() == ShifuTower.INSTANCE.getTOWER_DIMENSION()) ci.cancel();
    }
}
