package com.github.clevernucleus.dataattributes.mixin;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.github.clevernucleus.dataattributes.api.attribute.IEntityAttribute;
import com.github.clevernucleus.dataattributes.api.attribute.IEntityAttributeInstance;
import com.github.clevernucleus.dataattributes.api.attribute.StackingBehaviour;
import com.github.clevernucleus.dataattributes.api.attribute.FunctionBehaviour;
import com.github.clevernucleus.dataattributes.api.event.EntityAttributeModifiedEvents;
import com.github.clevernucleus.dataattributes.api.util.VoidConsumer;
import com.github.clevernucleus.dataattributes.mutable.MutableAttributeContainer;
import com.github.clevernucleus.dataattributes.mutable.MutableAttributeInstance;
import com.github.clevernucleus.dataattributes.mutable.MutableAttributeModifier;
import com.github.clevernucleus.dataattributes.mutable.MutableEntityAttribute;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.AttributeContainer;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

@Mixin(EntityAttributeInstance.class)
abstract class EntityAttributeInstanceMixin implements MutableAttributeInstance, IEntityAttributeInstance {
	
	@Unique
	private AttributeContainer data_containerCallback;
	
	@Unique
	private Identifier data_identifier;
	
	@Final
	@Shadow
	private EntityAttribute type;
	
	@Final
	@Shadow
	private Map<UUID, EntityAttributeModifier> idToModifiers;
	
	@Final
	@Shadow
	private Set<EntityAttributeModifier> persistentModifiers;
	
	@Shadow
	private double baseValue;
	
	@Shadow
	private Collection<EntityAttributeModifier> getModifiersByOperation(EntityAttributeModifier.Operation operation) {
		return Collections.emptySet();
	}
	
	@Shadow
	protected void onUpdate() {}
	
	@Inject(method = "<init>", at = @At("TAIL"))
	private void data_init(EntityAttribute type, Consumer<EntityAttributeInstance> updateCallback, CallbackInfo ci) {
		this.data_identifier = Registry.ATTRIBUTE.getId(type);
	}
	
	@Inject(method = "getAttribute", at = @At("HEAD"), cancellable = true)
	private void data_getAttribute(CallbackInfoReturnable<EntityAttribute> ci) {
		EntityAttribute attribute = Registry.ATTRIBUTE.get(this.data_identifier);
		
		if(attribute != null) {
			ci.setReturnValue(attribute);
		} else {
			ci.setReturnValue(this.type);
		}
	}
	
	@Inject(method = "computeValue", at = @At("HEAD"), cancellable = true)
	private void data_computeValue(CallbackInfoReturnable<Double> ci) {
		MutableEntityAttribute attribute = (MutableEntityAttribute)((EntityAttributeInstance)(Object)this).getAttribute();
		StackingBehaviour behaviour = attribute.stackingBehaviour();
		double k = 0.0D, v = 0.0D, k2 = 0.0D, v2 = 0.0D, km = 0.0D, vm = 0.0D;
		
		if(this.baseValue > 0.0D) {
			k = behaviour.stack(k, this.baseValue);
			k2 = behaviour.max(k2, this.baseValue);
		} else {
			v = behaviour.stack(v, this.baseValue);
			v2 = behaviour.max(v2, this.baseValue);
		}
		
		for(EntityAttributeModifier modifier : this.getModifiersByOperation(EntityAttributeModifier.Operation.ADDITION)) {
			double value = modifier.getValue();
			
			if(value > 0.0D) {
				k = behaviour.stack(k, value);
				k2 = behaviour.max(k2, value);
			} else {
				v = behaviour.stack(v, value);
				v2 = behaviour.max(v2, value);
			}
		}
		
		if(this.data_containerCallback != null) {
			Map<IEntityAttribute, Double> parents = ((MutableEntityAttribute)attribute).parentsMutable();
			
			for(IEntityAttribute parent : parents.keySet()) {
				EntityAttributeInstance instance = this.data_containerCallback.getCustomInstance((EntityAttribute)parent);
				
				if(instance == null) continue;
				
				double multiplier = parents.get(parent);
				double value = multiplier * instance.getValue();

				if (FunctionBehaviour.MULTIPLY.equals(parent.functionBehaviour())) {
					if (value > 0.0D) {
						km = behaviour.stack(km, value);
					} else {
						vm = behaviour.stack(vm, value);
					}
				} else {
					if (value > 0.0D) {
						k = behaviour.stack(k, value);
						// We don't put this here because follow-on attribute values should always be diminishing (if the attribute supports it).
						// k2 = behaviour.max(k2, value);
					} else {
						v = behaviour.stack(v, value);
						// We don't put this here because follow-on attribute values should always be diminishing (if the attribute supports it).
						// v2 = behaviour.max(v2, value);
					}
				}
			}
		}
		
		double d = attribute.sum(k, k2, v, v2);
		double e = d;
		
		for(EntityAttributeModifier modifier : this.getModifiersByOperation(EntityAttributeModifier.Operation.MULTIPLY_BASE)) {
			e += d * modifier.getValue();
		}

		e *= 1.0D + (km - vm);
		for(EntityAttributeModifier modifier : this.getModifiersByOperation(EntityAttributeModifier.Operation.MULTIPLY_TOTAL)) {
			e *= 1.0D + modifier.getValue();
		}
		
		double value = ((EntityAttribute)attribute).clamp(e);
		ci.setReturnValue(value);
	}
	
	@Inject(method = "addModifier", at = @At("HEAD"), cancellable = true)
	private void data_addModifier(EntityAttributeModifier modifier, CallbackInfo ci) {
		EntityAttributeInstance instance = (EntityAttributeInstance)(Object)this;
		UUID key = modifier.getId();
		EntityAttributeModifier entityAttributeModifier = (EntityAttributeModifier)this.idToModifiers.get(key);
		
		if(entityAttributeModifier != null) {
			throw new IllegalArgumentException("Modifier is already applied on this attribute!");
		} else {
			this.actionModifier(() -> {
				this.idToModifiers.put(key, modifier);
				instance.getModifiers(modifier.getOperation()).add(modifier);
			}, instance, modifier, true);
		}
		
		ci.cancel();
	}
	
	@Inject(method = "removeModifier", at = @At("HEAD"), cancellable = true)
	private void data_removeModifier(EntityAttributeModifier modifier, CallbackInfo ci) {
		EntityAttributeInstance instance = (EntityAttributeInstance)(Object)this;
		
		this.actionModifier(() -> {
			instance.getModifiers(modifier.getOperation()).remove(modifier);
			this.idToModifiers.remove(modifier.getId());
			this.persistentModifiers.remove(modifier);
		}, instance, modifier, false);
		
		ci.cancel();
	}
	
	@Redirect(method = "toNbt", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/registry/Registry;getId(Ljava/lang/Object;)Lnet/minecraft/util/Identifier;"))
	private Identifier data_toNbt(Registry<?> registry, Object type) {
		if(this.data_identifier == null) return Registry.ATTRIBUTE.getId((EntityAttribute)type);
		return this.data_identifier;
	}
	
	@Override
	public Identifier getId() {
		return this.data_identifier;
	}
	
	@Override
	public void actionModifier(final VoidConsumer consumerIn, final EntityAttributeInstance instanceIn, final EntityAttributeModifier modifierIn, final boolean isWasAdded) {
		EntityAttribute entityAttribute = ((EntityAttributeInstance)(Object)this).getAttribute();
		MutableEntityAttribute parent = (MutableEntityAttribute)entityAttribute;
		
		if(this.data_containerCallback == null) return;
		for(IEntityAttribute child : parent.childrenMutable().keySet()) {
			EntityAttribute attribute = (EntityAttribute)child;
			EntityAttributeInstance instance = this.data_containerCallback.getCustomInstance(attribute);
			
			if(instance != null) {
				instance.getValue();
			}
		}
		
		final double value = instanceIn.getValue();
		
		consumerIn.accept();
		
		this.onUpdate();
		
		LivingEntity livingEntity = ((MutableAttributeContainer)this.data_containerCallback).getLivingEntity();
		
		EntityAttributeModifiedEvents.MODIFIED.invoker().onModified(entityAttribute, livingEntity, modifierIn, value, isWasAdded);
		
		for(IEntityAttribute child : parent.childrenMutable().keySet()) {
			EntityAttribute attribute = (EntityAttribute)child;
			EntityAttributeInstance instance = this.data_containerCallback.getCustomInstance(attribute);
			
			if(instance != null) {
				((MutableAttributeInstance)instance).actionModifier(() -> {}, instance, modifierIn, isWasAdded);
			}
		}
	}
	
	@Override
	public void setContainerCallback(final AttributeContainer containerIn) {
		this.data_containerCallback = containerIn;
	}
	
	@Override
	public void updateId(final Identifier identifierIn) {
		this.data_identifier = identifierIn;
	}
	
	@Override
	public void updateModifier(final UUID uuid, final double value) {
		EntityAttributeInstance instance = (EntityAttributeInstance)(Object)this;
		EntityAttributeModifier modifier = instance.getModifier(uuid);
		
		if(modifier == null) return;
		
		this.actionModifier(() -> {
			((MutableAttributeModifier)modifier).updateValue(value);
		}, instance, modifier, false);
	}
	
	@Override
	public void refresh() {
		this.onUpdate();
	}
}
