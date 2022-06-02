package flaxbeard.immersivepetroleum.common.entity;

import java.util.List;

import com.mojang.math.Vector3f;

import blusunrize.immersiveengineering.api.Lib;
import blusunrize.immersiveengineering.common.util.IESounds;
import blusunrize.immersiveengineering.common.util.Utils;
import flaxbeard.immersivepetroleum.ImmersivePetroleum;
import flaxbeard.immersivepetroleum.api.energy.FuelHandler;
import flaxbeard.immersivepetroleum.common.IPContent.BoatUpgrades;
import flaxbeard.immersivepetroleum.common.IPContent.Items;
import flaxbeard.immersivepetroleum.common.items.DebugItem;
import flaxbeard.immersivepetroleum.common.items.MotorboatItem;
import flaxbeard.immersivepetroleum.common.network.IPPacketHandler;
import flaxbeard.immersivepetroleum.common.network.MessageConsumeBoatFuel;
import flaxbeard.immersivepetroleum.common.util.IPItemStackHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPaddleBoatPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.IndirectEntityDamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;

public class MotorboatEntity extends Boat implements IEntityAdditionalSpawnData{
	
	public static final EntityType<MotorboatEntity> TYPE = createType();
	
	private static EntityType<MotorboatEntity> createType(){
		EntityType<MotorboatEntity> ret = EntityType.Builder.<MotorboatEntity> of(MotorboatEntity::new, MobCategory.MISC).sized(1.375F, 0.5625F).build(ImmersivePetroleum.MODID + ":speedboat");
		ret.setRegistryName(ImmersivePetroleum.MODID, "speedboat");
		return ret;
	}
	
	public static EntityDataAccessor<Byte> getFlags(){
		return DATA_SHARED_FLAGS_ID;
	}
	
	/**
	 * Storage for {@link ResourceLocation} using
	 * {@link ResourceLocation#toString()}
	 */
	static final EntityDataAccessor<String> TANK_FLUID = SynchedEntityData.defineId(MotorboatEntity.class, EntityDataSerializers.STRING);
	static final EntityDataAccessor<Integer> TANK_AMOUNT = SynchedEntityData.defineId(MotorboatEntity.class, EntityDataSerializers.INT);
	
	static final EntityDataAccessor<ItemStack> UPGRADE_0 = SynchedEntityData.defineId(MotorboatEntity.class, EntityDataSerializers.ITEM_STACK);
	static final EntityDataAccessor<ItemStack> UPGRADE_1 = SynchedEntityData.defineId(MotorboatEntity.class, EntityDataSerializers.ITEM_STACK);
	static final EntityDataAccessor<ItemStack> UPGRADE_2 = SynchedEntityData.defineId(MotorboatEntity.class, EntityDataSerializers.ITEM_STACK);
	static final EntityDataAccessor<ItemStack> UPGRADE_3 = SynchedEntityData.defineId(MotorboatEntity.class, EntityDataSerializers.ITEM_STACK);
	
	public boolean isFireproof = false;
	public boolean hasIcebreaker = false;
	public boolean hasTank = false;
	public boolean hasRudders = false;
	public boolean hasPaddles = false;
	public boolean isBoosting = false;
	public float lastMoving;
	public float propellerRotation = 0F;
	
	public MotorboatEntity(Level world){
		this(TYPE, world);
	}
	
	public MotorboatEntity(Level world, double x, double y, double z){
		this(TYPE, world);
		setPos(x, y, z);
		this.xo = x;
		this.yo = y;
		this.zo = z;
	}
	
	public MotorboatEntity(EntityType<MotorboatEntity> type, Level world){
		super(type, world);
		this.blocksBuilding = true;
	}
	
	@Override
	protected void defineSynchedData(){
		super.defineSynchedData();
		this.entityData.define(TANK_FLUID, "");
		this.entityData.define(TANK_AMOUNT, Integer.valueOf(0));
		this.entityData.define(UPGRADE_0, ItemStack.EMPTY);
		this.entityData.define(UPGRADE_1, ItemStack.EMPTY);
		this.entityData.define(UPGRADE_2, ItemStack.EMPTY);
		this.entityData.define(UPGRADE_3, ItemStack.EMPTY);
	}
	
	@Override
	protected void readAdditionalSaveData(CompoundTag compound){
		super.readAdditionalSaveData(compound);
		
		String fluid = "";
		int amount = 0;
		ItemStack stack0 = ItemStack.EMPTY;
		ItemStack stack1 = ItemStack.EMPTY;
		ItemStack stack2 = ItemStack.EMPTY;
		ItemStack stack3 = ItemStack.EMPTY;
		
		if(compound.contains("tank")){
			CompoundTag tank = compound.getCompound("tank");
			fluid = tank.getString("fluid");
			amount = tank.getInt("amount");
		}
		
		if(compound.contains("upgrades")){
			CompoundTag upgrades = compound.getCompound("upgrades");
			stack0 = ItemStack.of(upgrades.getCompound("0"));
			stack1 = ItemStack.of(upgrades.getCompound("1"));
			stack2 = ItemStack.of(upgrades.getCompound("2"));
			stack3 = ItemStack.of(upgrades.getCompound("3"));
		}
		
		this.entityData.set(TANK_FLUID, fluid);
		this.entityData.set(TANK_AMOUNT, amount);
		this.entityData.set(UPGRADE_0, stack0);
		this.entityData.set(UPGRADE_1, stack1);
		this.entityData.set(UPGRADE_2, stack2);
		this.entityData.set(UPGRADE_3, stack3);
	}
	
	@Override
	protected void addAdditionalSaveData(CompoundTag compound){
		super.addAdditionalSaveData(compound);
		
		String fluid = this.entityData.get(TANK_FLUID);
		int amount = this.entityData.get(TANK_AMOUNT);
		ItemStack stack0 = this.entityData.get(UPGRADE_0);
		ItemStack stack1 = this.entityData.get(UPGRADE_1);
		ItemStack stack2 = this.entityData.get(UPGRADE_2);
		ItemStack stack3 = this.entityData.get(UPGRADE_3);
		
		CompoundTag tank = new CompoundTag();
		tank.putString("fluid", fluid);
		tank.putInt("amount", amount);
		compound.put("tank", tank);
		
		CompoundTag upgrades = new CompoundTag();
		upgrades.put("0", stack0.serializeNBT());
		upgrades.put("1", stack1.serializeNBT());
		upgrades.put("2", stack2.serializeNBT());
		upgrades.put("3", stack3.serializeNBT());
		compound.put("upgrades", upgrades);
	}
	
	public void setUpgrades(NonNullList<ItemStack> stacks){
		if(stacks != null && stacks.size() > 0){
			ItemStack o0 = stacks.get(0) == null ? ItemStack.EMPTY : stacks.get(0);
			ItemStack o1 = stacks.get(1) == null ? ItemStack.EMPTY : stacks.get(1);
			ItemStack o2 = stacks.get(2) == null ? ItemStack.EMPTY : stacks.get(2);
			ItemStack o3 = stacks.get(3) == null ? ItemStack.EMPTY : stacks.get(3);
			this.entityData.set(UPGRADE_0, o0);
			this.entityData.set(UPGRADE_1, o1);
			this.entityData.set(UPGRADE_2, o2);
			this.entityData.set(UPGRADE_3, o3);
		}
	}
	
	public boolean isLeftDown(){
		return this.inputLeft;
	}
	
	public boolean isRightDown(){
		return this.inputRight;
	}
	
	public boolean isForwardDown(){
		return this.inputUp;
	}
	
	public boolean isBackDown(){
		return this.inputDown;
	}
	
	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key){
		super.onSyncedDataUpdated(key);
		if(key == UPGRADE_0 || key == UPGRADE_1 || key == UPGRADE_2 || key == UPGRADE_3){
			NonNullList<ItemStack> upgrades = getUpgrades();
			this.isFireproof = false;
			this.hasIcebreaker = false;
			for(ItemStack upgrade:upgrades){
				if(upgrade != null && upgrade != ItemStack.EMPTY){
					Item item = upgrade.getItem();
					if(item == BoatUpgrades.REINFORCED_HULL.get()){
						this.isFireproof = true;
					}else if(item == BoatUpgrades.ICE_BREAKER.get()){
						this.hasIcebreaker = true;
					}else if(item == BoatUpgrades.TANK.get()){
						this.hasTank = true;
					}else if(item == BoatUpgrades.RUDDERS.get()){
						this.hasRudders = true;
					}else if(item == BoatUpgrades.PADDLES.get()){
						this.hasPaddles = true;
					}
				}
			}
		}
	}
	
	public void setContainedFluid(FluidStack stack){
		if(stack == null){
			this.entityData.set(TANK_FLUID, "");
			this.entityData.set(TANK_AMOUNT, 0);
		}else{
			this.entityData.set(TANK_FLUID, stack.getFluid() == null ? "" : stack.getFluid().getRegistryName().toString());
			this.entityData.set(TANK_AMOUNT, stack.getAmount());
		}
	}
	
	public FluidStack getContainedFluid(){
		String fluidName = this.entityData.get(TANK_FLUID);
		int amount = this.entityData.get(TANK_AMOUNT).intValue();
		
		if(fluidName == null || fluidName.isEmpty() || amount == 0)
			return FluidStack.EMPTY;
		
		Fluid fluid = ForgeRegistries.FLUIDS.getValue(new ResourceLocation(fluidName));
		if(fluid == null)
			return FluidStack.EMPTY;
		
		return new FluidStack(fluid, amount);
	}
	
	@Override
	public double getPassengersRidingOffset(){
		return isInLava() ? -0.1D + (3.9F / 16F) : -0.1D;
	}
	
	@Override
	public boolean hurt(DamageSource source, float amount){
		if(isInvulnerableTo(source) || (this.isFireproof && source.isFire())){
			return false;
		}else if(!this.level.isClientSide && isAlive()){
			if(source instanceof IndirectEntityDamageSource && source.getDirectEntity() != null && hasPassenger(source.getDirectEntity())){
				return false;
			}else{
				setHurtDir(-getHurtDir());
				setHurtTime(10);
				setDamage(getDamage() + amount * 10.0F);
				markHurt();
				boolean isPlayer = source.getDirectEntity() instanceof Player;
				boolean isCreativePlayer = isPlayer && ((Player) source.getDirectEntity()).getAbilities().instabuild;
				if((isCreativePlayer || getDamage() > 40.0F) && (!this.isFireproof || isPlayer) || (getDamage() > 240.0F)){
					if(!isCreativePlayer && this.level.getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)){
						MotorboatItem item = (MotorboatItem) getDropItem();
						ItemStack stack = new ItemStack(item, 1);
						
						IItemHandler handler = stack.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null).orElse(null);
						if(handler != null && handler instanceof IPItemStackHandler){
							NonNullList<ItemStack> upgrades = getUpgrades();
							for(int i = 0;i < handler.getSlots();i++){
								handler.insertItem(i, upgrades.get(i), false);
							}
						}
						
						writeTank(stack.getOrCreateTag(), true);
						
						if(isPlayer){
							Player player = (Player) source.getDirectEntity();
							if(!player.addItem(stack)){
								ItemEntity itemEntity = new ItemEntity(this.level, player.getX(), player.getY(), player.getZ(), stack);
								itemEntity.setNoPickUpDelay();
								this.level.addFreshEntity(itemEntity);
							}
						}else{
							spawnAtLocation(stack, 0F);
						}
					}
					
					remove(RemovalReason.DISCARDED);
				}
				
				return true;
			}
		}else{
			return true;
		}
	}
	
	public void readTank(CompoundTag nbt){
		FluidTank tank = new FluidTank(getMaxFuel());
		if(nbt != null)
			tank.readFromNBT(nbt.getCompound("tank"));
		
		setContainedFluid(tank.getFluid());
	}
	
	public void writeTank(CompoundTag nbt, boolean toItem){
		FluidTank tank = new FluidTank(getMaxFuel());
		tank.setFluid(getContainedFluid());
		
		boolean write = tank.getFluidAmount() > 0;
		if(!toItem || write)
			nbt.put("tank", tank.writeToNBT(new CompoundTag()));
	}
	
	@Override
	public InteractionResult interact(Player player, InteractionHand hand){
		ItemStack stack = player.getItemInHand(hand);
		
		if(stack != ItemStack.EMPTY && stack.getItem() instanceof DebugItem){
			((DebugItem) stack.getItem()).onSpeedboatClick(this, player, stack);
			return InteractionResult.SUCCESS;
		}
		
		if(Utils.isFluidRelatedItemStack(stack)){
			FluidStack fstack = FluidUtil.getFluidContained(stack).orElse(null);
			if(fstack != null){
				FluidTank tank = new FluidTank(getMaxFuel()){
					@Override
					public boolean isFluidValid(FluidStack stack){
						return FuelHandler.isValidBoatFuel(stack.getFluid());
					}
				};
				
				FluidStack fs = getContainedFluid();
				tank.setFluid(fs);
				
				FluidUtil.interactWithFluidHandler(player, hand, tank);
				
				setContainedFluid(tank.getFluid());
			}
			return InteractionResult.SUCCESS;
		}
		
		if(!this.level.isClientSide && !player.isShiftKeyDown() && this.outOfControlTicks < 60.0F && !player.isPassengerOfSameVehicle(this)){
			player.startRiding(this);
			return InteractionResult.SUCCESS;
		}
		
		return InteractionResult.FAIL;
	}
	
	@Override
	public void setInput(boolean p_184442_1_, boolean p_184442_2_, boolean p_184442_3_, boolean p_184442_4_){
		super.setInput(p_184442_1_, p_184442_2_, p_184442_3_, p_184442_4_);
		this.isBoosting = isEmergency() ? false : (inputUp && Minecraft.getInstance().options.keyJump.isDown());
	}
	
	@SuppressWarnings("deprecation")
	@Override
	public void tick(){
		this.oldStatus = this.status;
		this.status = this.getStatus();
		if(this.status != Boat.Status.UNDER_WATER && this.status != Boat.Status.UNDER_FLOWING_WATER){
			this.outOfControlTicks = 0.0F;
		}else{
			++this.outOfControlTicks;
		}
		
		if(!this.level.isClientSide && this.outOfControlTicks >= 60.0F){
			this.ejectPassengers();
		}
		
		if(this.getHurtTime() > 0){
			this.setHurtTime(this.getHurtTime() - 1);
		}
		
		if(this.getDamage() > 0.0F){
			this.setDamage(this.getDamage() - 1.0F);
		}
		
		this.xo = this.getX();
		this.yo = this.getY();
		this.zo = this.getZ();
		
		{ // From Entity.tick()
			if(!this.level.isClientSide){
				this.setSharedFlag(6, this.isCurrentlyGlowing());
			}
			this.baseTick();
		}
		this.tickLerp();
		
		if(this.isControlledByLocalInstance()){
			if(this.getPassengers().isEmpty() || !(this.getPassengers().get(0) instanceof Player)){
				this.setPaddleState(false, false);
			}
			
			this.floatBoat();
			if(this.level.isClientSide){
				this.controlBoat();
				this.level.sendPacketToServer(new ServerboundPaddleBoatPacket(this.getPaddleState(0), this.getPaddleState(1)));
			}
			
			this.move(MoverType.SELF, this.getDeltaMovement());
		}else{
			this.setDeltaMovement(Vec3.ZERO);
		}
		
		this.tickBubbleColumn();
		
		if(this.level.isClientSide){
			if(!isEmergency()){
				float moving = (this.inputUp || this.inputDown) ? (isBoosting ? .9F : .7F) : 0.5F;
				if(lastMoving != moving){
					ImmersivePetroleum.proxy.handleEntitySound(IESounds.dieselGenerator, this, false, .5f, 0.5F);
				}
				ImmersivePetroleum.proxy.handleEntitySound(IESounds.dieselGenerator, this, this.isVehicle() && this.getContainedFluid() != FluidStack.EMPTY && this.getContainedFluid().getAmount() > 0, this.inputUp || this.inputDown ? .5f : .3f, moving);
				lastMoving = moving;
				
				if(this.inputUp && this.level.random.nextInt(2) == 0){
					if(isInLava()){
						if(this.level.random.nextInt(3) == 0){
							float xO = (float) (Mth.sin(-this.getYRot() * 0.017453292F)) + (level.random.nextFloat() - .5F) * .3F;
							float zO = (float) (Mth.cos(this.getYRot() * 0.017453292F)) + (level.random.nextFloat() - .5F) * .3F;
							float yO = .4F + (level.random.nextFloat() - .5F) * .3F;
							Vec3 motion = getDeltaMovement();
							level.addParticle(ParticleTypes.LAVA, getX() - xO * 1.5F, getY() + yO, getZ() - zO * 1.5F, -2 * motion.x(), 0, -2 * motion.z());
						}
					}else{
						float xO = (float) (Mth.sin(-this.getYRot() * 0.017453292F)) + (level.random.nextFloat() - .5F) * .3F;
						float zO = (float) (Mth.cos(this.getYRot() * 0.017453292F)) + (level.random.nextFloat() - .5F) * .3F;
						float yO = .1F + (level.random.nextFloat() - .5F) * .3F;
						level.addParticle(ParticleTypes.BUBBLE, getX() - xO * 1.5F, getY() + yO, getZ() - zO * 1.5F, 0, 0, 0);
					}
				}
				if(isBoosting && this.level.random.nextInt(2) == 0){
					float xO = (float) (Mth.sin(-this.getYRot() * 0.017453292F)) + (level.random.nextFloat() - .5F) * .3F;
					float zO = (float) (Mth.cos(this.getYRot() * 0.017453292F)) + (level.random.nextFloat() - .5F) * .3F;
					float yO = .8F + (level.random.nextFloat() - .5F) * .3F;
					level.addParticle(ParticleTypes.SMOKE, getX() - xO * 1.3F, getY() + yO, getZ() - zO * 1.3F, 0, 0, 0);
				}
			}else{
				ImmersivePetroleum.proxy.handleEntitySound(IESounds.dieselGenerator, this, false, .5f, 0.5F);
			}
		}
		
		if(this.isEmergency()){
			for(int i = 0;i <= 1;++i){
				if(this.getPaddleState(i)){
					this.paddlePositions[i] = (float) ((double) this.paddlePositions[i] + (double) ((float) Math.PI / 4F));
				}else{
					this.paddlePositions[i] = 0.0F;
				}
			}
		}else{
			if(this.getPaddleState(0)){
				this.paddlePositions[0] = (float) ((double) this.paddlePositions[0] + (isBoosting ? 0.02D : 0.01D));
			}else if(this.getPaddleState(1)){
				this.paddlePositions[0] = (float) ((double) this.paddlePositions[0] - 0.01D);
			}
		}
		
		float xO = (float) (Mth.sin(-this.getYRot() * 0.017453292F));
		float zO = (float) (Mth.cos(this.getYRot() * 0.017453292F));
		Vector3f vec = normalizeVector(new Vector3f(xO, zO, 0.0F));
		
		if(this.hasIcebreaker && !isEmergency()){
			AABB bb = getBoundingBox().inflate(0.1);
			BlockPos.MutableBlockPos mutableBlockPos0 = new BlockPos.MutableBlockPos(bb.minX + 0.001D, bb.minY + 0.001D, bb.minZ + 0.001D);
			BlockPos.MutableBlockPos mutableBlockPos1 = new BlockPos.MutableBlockPos(bb.maxX - 0.001D, bb.maxY - 0.001D, bb.maxZ - 0.001D);
			BlockPos.MutableBlockPos mutableBlockPos2 = new BlockPos.MutableBlockPos();
			
			if(this.level.hasChunksAt(mutableBlockPos0, mutableBlockPos1)){
				for(int i = mutableBlockPos0.getX();i <= mutableBlockPos1.getX();++i){
					for(int j = mutableBlockPos0.getY();j <= mutableBlockPos1.getY();++j){
						for(int k = mutableBlockPos0.getZ();k <= mutableBlockPos1.getZ();++k){
							mutableBlockPos2.set(i, j, k);
							BlockState BlockState = this.level.getBlockState(mutableBlockPos2);
							
							Vector3f vec2 = new Vector3f((float) (i + 0.5f - getX()), (float) (k + 0.5f - getZ()), 0.0F);
							normalizeVector(vec2);
							
							float sim = dotVector(vec2, vec);
							
							if(BlockState.getBlock() == Blocks.ICE && sim > .3f){
								this.level.destroyBlock(mutableBlockPos2, false);
								this.level.setBlockAndUpdate(mutableBlockPos2, Blocks.WATER.defaultBlockState());
							}
						}
					}
				}
			}
		}
		
		this.checkInsideBlocks();
		List<Entity> list = this.level.getEntities(this, this.getBoundingBox().inflate((double) 0.2F, (double) -0.01F, (double) 0.2F), EntitySelector.pushableBy(this));
		if(!list.isEmpty()){
			boolean flag = !this.level.isClientSide && !(this.getControllingPassenger() instanceof Player);
			
			for(int j = 0;j < list.size();++j){
				Entity entity = list.get(j);
				
				if(!entity.hasPassenger(this)){
					if(flag && this.getPassengers().size() < 2 && !entity.isPassenger() && entity.getBbWidth() < this.getBbWidth() && entity instanceof LivingEntity && !(entity instanceof WaterAnimal) && !(entity instanceof Player)){
						entity.startRiding(this);
					}else{
						this.push(entity);
						
						if(this.hasIcebreaker){
							if(entity instanceof LivingEntity && !(entity instanceof Player) && this.getControllingPassenger() instanceof Player){
								Vector3f vec2 = new Vector3f((float) (entity.getX() - getX()), (float) (entity.getZ() - getZ()), 0.0F);
								normalizeVector(vec2);
								
								float sim = dotVector(vec2, vec);
								
								if(sim > .5f){
									Vec3 motion = entity.getDeltaMovement();
									entity.hurt(DamageSource.playerAttack((Player) this.getControllingPassenger()), 4);
									entity.setDeltaMovement(new Vec3(motion.x + (vec2.x() * .75F), motion.y, motion.z + (vec2.y() * .75F)));
								}
							}
						}
					}
				}
			}
		}
	}
	
	/** Because fuck you for making that client side only */
	private Vector3f normalizeVector(Vector3f vec){
		float f = vec.x() * vec.x() + vec.y() * vec.y() + vec.z() * vec.z();
		if(!((double) f < 1.0E-5D)){
			float f1 = 1 / Mth.sqrt(f);
			vec.setX(vec.x() * f1);
			vec.setX(vec.y() * f1);
			vec.setX(vec.z() * f1);
		}
		return vec;
	}
	
	/** Because fuck you for making that client side only */
	private float dotVector(Vector3f a, Vector3f b){
		return a.x() * b.x() + a.y() * b.y() + a.z() * b.z();
	}
	
	@Override
	protected void controlBoat(){
		if(isVehicle()){
			float f = 0.0F;
			
			if(isEmergency()){
				if(this.inputLeft){
					--this.deltaRotation;
				}
				
				if(this.inputRight){
					++this.deltaRotation;
				}
				
				if(this.inputRight != this.inputLeft && !this.inputUp && !this.inputDown){
					f += 0.005F;
				}

				this.setYRot(this.getYRot() + this.deltaRotation);
				if(this.inputUp){
					f += 0.04F;
				}
				
				if(this.inputDown){
					f -= 0.005F;
				}
				
				this.setDeltaMovement(this.getDeltaMovement().add((double) (Mth.sin(-this.getYRot() * ((float) Math.PI / 180F)) * f), 0.0D, (double) (Mth.cos(this.getYRot() * ((float) Math.PI / 180F)) * f)));
				this.setPaddleState(this.inputRight && !this.inputLeft || this.inputUp, this.inputLeft && !this.inputRight || this.inputUp);
			}else{
				FluidStack fluid = getContainedFluid();
				int consumeAmount = 0;
				if(fluid != FluidStack.EMPTY){
					consumeAmount = FuelHandler.getBoatFuelUsedPerTick(fluid.getFluid());
				}
				
				if(fluid != FluidStack.EMPTY && fluid.getAmount() >= consumeAmount && (this.inputUp || this.inputDown)){
					int toConsume = consumeAmount;
					if(this.inputUp){
						f += 0.05F;
						if(this.isBoosting && fluid.getAmount() >= 3 * consumeAmount){
							f *= 1.6;
							toConsume *= 3;
						}
					}
					
					if(this.inputDown){
						f -= 0.01F;
					}
					
					fluid.setAmount(Math.max(0, fluid.getAmount() - toConsume));
					setContainedFluid(fluid);
					
					if(this.level.isClientSide)
						IPPacketHandler.sendToServer(new MessageConsumeBoatFuel(toConsume));
					
					setPaddleState(this.inputUp, this.inputDown);
				}else{
					setPaddleState(false, false);
				}
				
				Vec3 motion = this.getDeltaMovement().add((double) (Mth.sin(-this.getYRot() * ((float) Math.PI / 180F)) * f), 0.0D, (double) (Mth.cos(this.getYRot() * ((float) Math.PI / 180F)) * f));
				
				if(this.inputLeft || this.inputRight){
					float speed = Mth.sqrt((float) (motion.x * motion.x + motion.z * motion.z));
					
					if(this.inputRight){
						this.deltaRotation += 1.1F * speed * (this.hasRudders ? 1.5F : 1F) * (this.isBoosting ? 0.5F : 1) * (this.inputDown && !this.inputUp ? 2F : 1F);
						
						this.propellerRotation = Mth.clamp(this.propellerRotation - 0.2F, -1.0F, 1.0F);
					}
					
					if(this.inputLeft){
						this.deltaRotation -= 1.1F * speed * (this.hasRudders ? 1.5F : 1F) * (this.isBoosting ? 0.5F : 1) * (this.inputDown && !this.inputUp ? 2F : 1F);
						
						this.propellerRotation = Mth.clamp(this.propellerRotation + 0.2F, -1.0F, 1.0F);
					}
				}
				
				if(!this.inputLeft && !this.inputRight && this.propellerRotation != 0.0F){
					this.propellerRotation *= 0.7F;
					if(this.propellerRotation > -1.0E-2F && this.propellerRotation < 1.0E-2F){
						this.propellerRotation = 0;
					}
				}

				this.setYRot(this.getYRot() + this.deltaRotation);

				this.setDeltaMovement(motion);
				this.setPaddleState((this.inputRight && !this.inputLeft || this.inputUp), (this.inputLeft && !this.inputRight || this.inputUp));
			}
		}
	}
	
	public int getMaxFuel(){
		return this.hasTank ? 16000 : 8000;
	}
	
	@Override
	public Item getDropItem(){
		return Items.SPEEDBOAT.get();
	}
	
	@Override
	public boolean isOnFire(){
		if(this.isFireproof)
			return false;
		
		return super.isOnFire();
	}
	
	public boolean isEmergency(){
		FluidStack fluid = getContainedFluid();
		if(fluid != FluidStack.EMPTY){
			int consumeAmount = FuelHandler.getBoatFuelUsedPerTick(fluid.getFluid());
			return fluid.getAmount() < consumeAmount && this.hasPaddles;
		}
		
		return this.hasPaddles;
	}
	
	public NonNullList<ItemStack> getUpgrades(){
		NonNullList<ItemStack> stackList = NonNullList.withSize(4, ItemStack.EMPTY);
		stackList.set(0, this.entityData.get(UPGRADE_0));
		stackList.set(1, this.entityData.get(UPGRADE_1));
		stackList.set(2, this.entityData.get(UPGRADE_2));
		stackList.set(3, this.entityData.get(UPGRADE_3));
		return stackList;
	}
	
	public String[] getOverlayText(Player player, HitResult mop){
		if(Utils.isFluidRelatedItemStack(player.getItemInHand(InteractionHand.MAIN_HAND))){
			String s = null;
			FluidStack stack = getContainedFluid();
			if(stack != FluidStack.EMPTY){
				s = stack.getDisplayName().getString() + ": " + stack.getAmount() + "mB";
			}else{
				s = I18n.get(Lib.GUI + "empty");
			}
			return new String[]{s};
			
		}
		return null;
	}
	
	@Override
	public float getWaterLevelAbove(){
		AABB axisalignedbb = this.getBoundingBox();
		int i = Mth.floor(axisalignedbb.minX);
		int j = Mth.ceil(axisalignedbb.maxX);
		int k = Mth.floor(axisalignedbb.maxY);
		int l = Mth.ceil(axisalignedbb.maxY - this.lastYd);
		int i1 = Mth.floor(axisalignedbb.minZ);
		int j1 = Mth.ceil(axisalignedbb.maxZ);
		BlockPos.MutableBlockPos blockpos$mutable = new BlockPos.MutableBlockPos();
		
		label39: for(int k1 = k;k1 < l;++k1){
			float f = 0.0F;
			
			for(int l1 = i;l1 < j;++l1){
				for(int i2 = i1;i2 < j1;++i2){
					blockpos$mutable.set(l1, k1, i2);
					FluidState fluidstate = this.level.getFluidState(blockpos$mutable);
					if(fluidstate.is(FluidTags.WATER) || (this.isFireproof && fluidstate.is(FluidTags.LAVA))){
						f = Math.max(f, fluidstate.getHeight(this.level, blockpos$mutable));
					}
					
					if(f >= 1.0F){
						continue label39;
					}
				}
			}
			
			if(f < 1.0F){
				return (float) blockpos$mutable.getY() + f;
			}
		}
		
		return (float) (l + 1);
	}
	
	@Override
	protected boolean checkInWater(){
		AABB axisalignedbb = this.getBoundingBox();
		int i = Mth.floor(axisalignedbb.minX);
		int j = Mth.ceil(axisalignedbb.maxX);
		int k = Mth.floor(axisalignedbb.minY);
		int l = Mth.ceil(axisalignedbb.minY + 0.001D);
		int i1 = Mth.floor(axisalignedbb.minZ);
		int j1 = Mth.ceil(axisalignedbb.maxZ);
		boolean flag = false;
		this.waterLevel = Double.MIN_VALUE;
		BlockPos.MutableBlockPos blockpos$mutable = new BlockPos.MutableBlockPos();
		
		for(int k1 = i;k1 < j;++k1){
			for(int l1 = k;l1 < l;++l1){
				for(int i2 = i1;i2 < j1;++i2){
					blockpos$mutable.set(k1, l1, i2);
					FluidState fluidstate = this.level.getFluidState(blockpos$mutable);
					if(fluidstate.is(FluidTags.WATER) || (this.isFireproof && fluidstate.is(FluidTags.LAVA))){
						float f = (float) l1 + fluidstate.getHeight(this.level, blockpos$mutable);
						this.waterLevel = Math.max((double) f, this.waterLevel);
						flag |= axisalignedbb.minY < (double) f;
					}
				}
			}
		}
		
		return flag;
	}
	
	@Override
	protected Status isUnderwater(){
		AABB axisalignedbb = this.getBoundingBox();
		double d0 = axisalignedbb.maxY + 0.001D;
		int i = Mth.floor(axisalignedbb.minX);
		int j = Mth.ceil(axisalignedbb.maxX);
		int k = Mth.floor(axisalignedbb.maxY);
		int l = Mth.ceil(d0);
		int i1 = Mth.floor(axisalignedbb.minZ);
		int j1 = Mth.ceil(axisalignedbb.maxZ);
		boolean flag = false;
		BlockPos.MutableBlockPos blockpos$mutable = new BlockPos.MutableBlockPos();
		
		for(int k1 = i;k1 < j;++k1){
			for(int l1 = k;l1 < l;++l1){
				for(int i2 = i1;i2 < j1;++i2){
					blockpos$mutable.set(k1, l1, i2);
					FluidState fluidstate = this.level.getFluidState(blockpos$mutable);
					if((fluidstate.is(FluidTags.WATER) || ((this.isFireproof && fluidstate.is(FluidTags.LAVA)))) && d0 < (double) ((float) blockpos$mutable.getY() + fluidstate.getHeight(this.level, blockpos$mutable))){
						if(!fluidstate.isSource()){
							return Boat.Status.UNDER_FLOWING_WATER;
						}
						
						flag = true;
					}
				}
			}
		}
		
		return flag ? Boat.Status.UNDER_WATER : null;
	}
	
	public boolean isLeftInDown(){
		return this.inputLeft;
	}
	
	public boolean isRightInDown(){
		return this.inputRight;
	}
	
	public boolean isForwardInDown(){
		return this.inputUp;
	}
	
	public boolean isBackInDown(){
		return this.inputDown;
	}
	
	@Override
	public boolean getSharedFlag(int flag){
		return super.getSharedFlag(flag);
	}
	
	@Override
	public void setSharedFlag(int flag, boolean set){
		super.setSharedFlag(flag, set);
	}
	
	@Override
	public Packet<?> getAddEntityPacket(){
		return NetworkHooks.getEntitySpawningPacket(this);
	}
	
	@Override
	public void readSpawnData(FriendlyByteBuf buffer){
		String fluid = buffer.readUtf();
		int amount = buffer.readInt();
		ItemStack stack0 = buffer.readItem();
		ItemStack stack1 = buffer.readItem();
		ItemStack stack2 = buffer.readItem();
		ItemStack stack3 = buffer.readItem();
		
		this.entityData.set(TANK_FLUID, fluid);
		this.entityData.set(TANK_AMOUNT, amount);
		this.entityData.set(UPGRADE_0, stack0);
		this.entityData.set(UPGRADE_1, stack1);
		this.entityData.set(UPGRADE_2, stack2);
		this.entityData.set(UPGRADE_3, stack3);
	}
	
	@Override
	public void writeSpawnData(FriendlyByteBuf buffer){
		String fluid = this.entityData.get(TANK_FLUID);
		int amount = this.entityData.get(TANK_AMOUNT);
		ItemStack stack0 = this.entityData.get(UPGRADE_0);
		ItemStack stack1 = this.entityData.get(UPGRADE_1);
		ItemStack stack2 = this.entityData.get(UPGRADE_2);
		ItemStack stack3 = this.entityData.get(UPGRADE_3);
		
		buffer.writeUtf(fluid);
		buffer.writeInt(amount);
		buffer.writeItem(stack0);
		buffer.writeItem(stack1);
		buffer.writeItem(stack2);
		buffer.writeItem(stack3);
	}
}
