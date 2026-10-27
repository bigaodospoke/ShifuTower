package com.shifutower.mixin;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonServerDelegate;
import com.shifutower.ShifuTower;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * É aqui (no fim da animação de desmaio) que o Cobblemon solta uma cópia do item segurado e os drops da espécie
 * de um Pokémon "selvagem". Na torre todo Pokémon que desmaia é uma cópia da batalha, então nada pode cair:
 * senão daria para duplicar os itens do time do jogador.
 */
@Mixin(value = PokemonServerDelegate.class, remap = false)
public abstract class PokemonServerDelegateMixin {
    @Inject(method = "doDeathDrops", at = @At("HEAD"), cancellable = true)
    private void shifutower$semDropsNaTorre(CallbackInfo ci) {
        PokemonEntity entity = ((PokemonServerDelegate) (Object) this).getEntity();
        if (entity.level().dimension() == ShifuTower.INSTANCE.getTOWER_DIMENSION()) ci.cancel();
    }
}
