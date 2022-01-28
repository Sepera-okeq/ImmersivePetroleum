package flaxbeard.immersivepetroleum.common.blocks.tileentities;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.ImmutableSet;

import blusunrize.immersiveengineering.api.IEEnums.IOSideConfig;
import blusunrize.immersiveengineering.api.crafting.MultiblockRecipe;
import blusunrize.immersiveengineering.api.utils.shapes.CachedShapesWithTransform;
import blusunrize.immersiveengineering.common.blocks.IEBlockInterfaces.IBlockBounds;
import blusunrize.immersiveengineering.common.blocks.generic.PoweredMultiblockTileEntity;
import flaxbeard.immersivepetroleum.api.crafting.reservoir.ReservoirHandler;
import flaxbeard.immersivepetroleum.api.crafting.reservoir.ReservoirIsland;
import flaxbeard.immersivepetroleum.common.IPTileTypes;
import flaxbeard.immersivepetroleum.common.cfg.IPServerConfig;
import flaxbeard.immersivepetroleum.common.multiblocks.PumpjackMultiblock;
import flaxbeard.immersivepetroleum.common.util.FluidHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.Direction;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ColumnPos;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fluids.capability.templates.FluidTank;

public class PumpjackTileEntity extends PoweredMultiblockTileEntity<PumpjackTileEntity, MultiblockRecipe> implements IBlockBounds{
	/** Template-Location of the Energy Input Port. (0, 1, 5) */
	public static final Set<BlockPos> Redstone_IN = ImmutableSet.of(new BlockPos(0, 1, 5));
	
	/** Template-Location of the Redstone Input Port. (2, 1, 5) */
	public static final Set<BlockPos> Energy_IN = ImmutableSet.of(new BlockPos(2, 1, 5));
	
	/** Template-Location of the Eastern Fluid Output Port. (2, 0, 2) */
	public static final BlockPos East_Port = new BlockPos(2, 0, 2);
	
	/** Template-Location of the Western Fluid Output Port. (0, 0, 2) */
	public static final BlockPos West_Port = new BlockPos(0, 0, 2);
	
	/**
	 * Template-Location of the Bottom Fluid Output Port. (1, 0, 0) <b>(Also
	 * Master Block)</b>
	 */
	public static final BlockPos Down_Port = new BlockPos(1, 0, 0);
	
	public FluidTank fakeTank = new FluidTank(0);
	public boolean wasActive = false;
	public float activeTicks = 0;
	public PumpjackTileEntity(){
		super(PumpjackMultiblock.INSTANCE, 16000, true, null);
	}
	
	@Override
	public TileEntityType<?> getType(){
		return IPTileTypes.PUMP.get();
	}
	
	@Override
	public void readCustomNBT(CompoundNBT nbt, boolean descPacket){
		super.readCustomNBT(nbt, descPacket);
		boolean lastActive = this.wasActive;
		this.wasActive = nbt.getBoolean("wasActive");
		if(!this.wasActive && lastActive){
			this.activeTicks++;
		}
	}
	
	@Override
	public void writeCustomNBT(CompoundNBT nbt, boolean descPacket){
		super.writeCustomNBT(nbt, descPacket);
		nbt.putBoolean("wasActive", this.wasActive);
	}
	
	@Override
	public void tick(){
		super.tick();
		
		if((this.world.isRemote || isDummy()) && this.wasActive){
			this.activeTicks++;
			return;
		}
		
		boolean active = false;
		
		if(!isRSDisabled()){
			TileEntity teLow = this.getWorldNonnull().getTileEntity(this.pos.down());
			
			if(teLow instanceof WellPipeTileEntity){
				WellTileEntity well = ((WellPipeTileEntity) teLow).getWell();
				
				boolean debug = true;
				if(well != null && debug){
					int consumption = IPServerConfig.EXTRACTION.pumpjack_consumption.get();
					int extracted = this.energyStorage.extractEnergy(consumption, true);
					
					if(extracted >= consumption){
						// Does any island still have pressure?
						boolean foundPressurizedIsland = false;
						for(ColumnPos cPos:well.tappedIslands){
							ReservoirIsland island = ReservoirHandler.getIsland(this.world, cPos);
							
							if(island != null && island.getPressure(getWorldNonnull(), cPos.x, cPos.z) > 0.0F){
								foundPressurizedIsland = true;
								break;
							}
						}
						
						if(!foundPressurizedIsland){
							int extractSpeed = IPServerConfig.EXTRACTION.pumpjack_speed.get();
							
							Direction portEast_facing = getIsMirrored() ? getFacing().rotateYCCW() : getFacing().rotateY();
							Direction portWest_facing = getIsMirrored() ? getFacing().rotateY() : getFacing().rotateYCCW();
							
							BlockPos portEast_pos = getBlockPosForPos(East_Port).offset(portEast_facing);
							BlockPos portWest_pos = getBlockPosForPos(West_Port).offset(portWest_facing);
							
							IFluidHandler portEast_output = FluidUtil.getFluidHandler(this.world, portEast_pos, portEast_facing.getOpposite()).orElse(null);
							IFluidHandler portWest_output = FluidUtil.getFluidHandler(this.world, portWest_pos, portWest_facing.getOpposite()).orElse(null);
							
							for(ColumnPos cPos:well.tappedIslands){
								ReservoirIsland island = ReservoirHandler.getIsland(this.world, cPos);
								if(island != null){
									FluidStack fluid = new FluidStack(island.getType().getFluid(), island.extract(extractSpeed, FluidAction.SIMULATE));
									
									if(portEast_output != null){
										int accepted = portEast_output.fill(fluid, FluidAction.SIMULATE);
										if(accepted > 0){
											int drained = portEast_output.fill(FluidHelper.copyFluid(fluid, Math.min(fluid.getAmount(), accepted)), FluidAction.EXECUTE);
											island.extract(drained, FluidAction.EXECUTE);
											fluid = FluidHelper.copyFluid(fluid, fluid.getAmount() - drained);
											active = true;
										}
									}
									
									if(portWest_output != null && fluid.getAmount() > 0){
										int accepted = portWest_output.fill(fluid, FluidAction.SIMULATE);
										if(accepted > 0){
											int drained = portWest_output.fill(FluidHelper.copyFluid(fluid, Math.min(fluid.getAmount(), accepted)), FluidAction.EXECUTE);
											island.extract(drained, FluidAction.EXECUTE);
											active = true;
										}
									}
								}
							}
							
							if(active){
								this.energyStorage.extractEnergy(consumption, false);
								this.activeTicks++;
							}
						}
					}
				}
			}
		}
		
		if(active != this.wasActive){
			this.markDirty();
			this.markContainingBlockForUpdate(null);
		}
		
		this.wasActive = active;
	}
	
	@Override
	public Set<BlockPos> getEnergyPos(){
		return Energy_IN;
	}
	
	@Override
	public IOSideConfig getEnergySideConfig(Direction facing){
		if(this.formed && this.isEnergyPos() && (facing == null || facing == Direction.UP))
			return IOSideConfig.INPUT;
		
		return IOSideConfig.NONE;
	}
	
	@Override
	public Set<BlockPos> getRedstonePos(){
		return Redstone_IN;
	}
	
	@Override
	public boolean isInWorldProcessingMachine(){
		return false;
	}
	
	@Override
	public void doProcessOutput(ItemStack output){
	}
	
	@Override
	public void doProcessFluidOutput(FluidStack output){
	}
	
	@Override
	public void onProcessFinish(MultiblockProcess<MultiblockRecipe> process){
	}
	
	@Override
	public boolean additionalCanProcessCheck(MultiblockProcess<MultiblockRecipe> process){
		return false;
	}
	
	@Override
	public float getMinProcessDistance(MultiblockProcess<MultiblockRecipe> process){
		return 0;
	}
	
	@Override
	public int getMaxProcessPerTick(){
		return 1;
	}
	
	@Override
	public int getProcessQueueMaxLength(){
		return 1;
	}
	
	@Override
	public boolean isStackValid(int slot, ItemStack stack){
		return true;
	}
	
	@Override
	public int getSlotLimit(int slot){
		return 64;
	}
	
	@Override
	public int[] getOutputSlots(){
		return null;
	}
	
	@Override
	public int[] getOutputTanks(){
		return new int[]{1};
	}
	
	@Override
	public void doGraphicalUpdates(){
		this.markDirty();
		this.markContainingBlockForUpdate(null);
	}
	
	@Override
	public MultiblockRecipe findRecipeForInsertion(ItemStack inserting){
		return null;
	}
	
	@Override
	protected MultiblockRecipe getRecipeForId(ResourceLocation id){
		return null;
	}
	
	@Override
	public NonNullList<ItemStack> getInventory(){
		return null;
	}
	
	@Override
	public IFluidTank[] getInternalTanks(){
		return null;
	}
	
	@Override
	protected IFluidTank[] getAccessibleFluidTanks(Direction side){
		PumpjackTileEntity master = master();
		if(master != null){
			// East Port
			if(this.posInMultiblock.equals(East_Port)){
				if(side == null || (getIsMirrored() ? (side == getFacing().rotateYCCW()) : (side == getFacing().rotateY()))){
					return new FluidTank[]{master.fakeTank};
				}
			}
			
			// West Port
			if(this.posInMultiblock.equals(West_Port)){
				if(side == null || (getIsMirrored() ? (side == getFacing().rotateY()) : (side == getFacing().rotateYCCW()))){
					return new FluidTank[]{master.fakeTank};
				}
			}
		}
		return new FluidTank[0];
	}
	
	@Override
	protected boolean canFillTankFrom(int iTank, Direction side, FluidStack resource){
		return false;
	}
	
	@Override
	protected boolean canDrainTankFrom(int iTank, Direction side){
		return false;
	}
	
	private static CachedShapesWithTransform<BlockPos, Pair<Direction, Boolean>> SHAPES = CachedShapesWithTransform.createForMultiblock(PumpjackTileEntity::getShape);
	@Override
	public VoxelShape getBlockBounds(ISelectionContext ctx){
		return SHAPES.get(this.posInMultiblock, Pair.of(getFacing(), getIsMirrored()));
	}
	
	private static List<AxisAlignedBB> getShape(BlockPos posInMultiblock){
		final int bX = posInMultiblock.getX();
		final int bY = posInMultiblock.getY();
		final int bZ = posInMultiblock.getZ();
		
		// Most of the arm doesnt need collision. Dumb anyway.
		if((bY == 3 && bX == 1 && bZ != 2) || (bX == 1 && bY == 2 && bZ == 0)){
			return new ArrayList<>();
		}
		
		// Motor
		if(bY < 3 && bX == 1 && bZ == 4){
			List<AxisAlignedBB> list = new ArrayList<>();
			if(bY == 2){
				list.add(new AxisAlignedBB(0.25, 0.0, 0.0, 0.75, 0.25, 1.0));
			}else{
				list.add(new AxisAlignedBB(0.25, 0.0, 0.0, 0.75, 1.0, 1.0));
			}
			if(bY == 0){
				list.add(new AxisAlignedBB(0.0, 0.0, 0.0, 1.0, 0.5, 1.0));
			}
			return list;
		}
		
		// Support
		if(bZ == 2 && bY > 0){
			if(bX == 0){
				if(bY == 1){
					List<AxisAlignedBB> list = new ArrayList<>();
					list.add(new AxisAlignedBB(0.6875, 0.0, 0.0, 1.0, 1.0, 0.25));
					list.add(new AxisAlignedBB(0.6875, 0.0, 0.75, 1.0, 1.0, 1.0));
					return list;
				}
				if(bY == 2){
					List<AxisAlignedBB> list = new ArrayList<>();
					list.add(new AxisAlignedBB(0.8125, 0.0, 0.0, 1.0, 0.5, 1.0));
					list.add(new AxisAlignedBB(0.8125, 0.5, 0.25, 1.0, 1.0, 0.75));
					return list;
				}
				if(bY == 3){
					return Arrays.asList(new AxisAlignedBB(0.9375, 0.0, 0.375, 1.0, 0.125, 0.625));
				}
			}
			if(bX == 1 && bY == 3){
				return Arrays.asList(new AxisAlignedBB(0.0, -0.125, 0.375, 1.0, 0.125, 0.625));
			}
			if(bX == 2){
				if(bY == 1){
					List<AxisAlignedBB> list = new ArrayList<>();
					list.add(new AxisAlignedBB(0.0, 0.0, 0.0, 0.3125, 1.0, 0.25));
					list.add(new AxisAlignedBB(0.0, 0.0, 0.75, 0.3125, 1.0, 1.0));
					return list;
				}
				if(bY == 2){
					List<AxisAlignedBB> list = new ArrayList<>();
					list.add(new AxisAlignedBB(0.0, 0.0, 0.0, 0.1875, 0.5, 1.0));
					list.add(new AxisAlignedBB(0.0, 0.5, 0.25, 0.1875, 1.0, 0.75));
					return list;
				}
				if(bY == 3){
					return Arrays.asList(new AxisAlignedBB(0.0, 0.0, 0.375, 0.0625, 0.125, 0.625));
				}
			}
		}
		
		// Redstone Controller
		if(bX == 0 && bZ == 5){
			if(bY == 0){ // Bottom
				return Arrays.asList(
						new AxisAlignedBB(0.0, 0.0, 0.0, 1.0, 0.5, 1.0),
						new AxisAlignedBB(0.75, 0.0, 0.625, 0.875, 1.0, 0.875),
						new AxisAlignedBB(0.125, 0.0, 0.625, 0.25, 1.0, 0.875)
				);
			}
			if(bY == 1){ // Top
				return Arrays.asList(new AxisAlignedBB(0.0, 0.0, 0.5, 1.0, 1.0, 1.0));
			}
		}
		
		// Below the power-in block, base height
		if(bX == 2 && bY == 0 && bZ == 5){
			return Arrays.asList(new AxisAlignedBB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0));
		}
		
		// Misc
		if(bY == 0){
			
			// Legs Bottom Front
			if(bZ == 1 && (bX == 0 || bX == 2)){
				List<AxisAlignedBB> list = new ArrayList<>();
				
				list.add(new AxisAlignedBB(0.0, 0.0, 0.0, 1.0, 0.5, 1.0));
				
				if(bX == 0){
					list.add(new AxisAlignedBB(0.5, 0.5, 0.5, 1.0, 1.0, 1.0));
				}
				if(bX == 2){
					list.add(new AxisAlignedBB(0.0, 0.5, 0.5, 0.5, 1.0, 1.0));
				}
				
				return list;
			}
			
			// Legs Bottom Back
			if(bZ == 3 && (bX == 0 || bX == 2)){
				List<AxisAlignedBB> list = new ArrayList<>();
				
				list.add(new AxisAlignedBB(0.0, 0.0, 0.0, 1.0, 0.5, 1.0));
				
				if(bX == 0){
					list.add(new AxisAlignedBB(0.5, 0.5, 0.0, 1.0, 1.0, 0.5));
				}
				if(bX == 2){
					list.add(new AxisAlignedBB(0.0, 0.5, 0.0, 0.5, 1.0, 0.5));
				}
				
				return list;
			}
			
			// Fluid Outputs
			if(bZ == 2 && (bX == 0 || bX == 2)){
				return Arrays.asList(new AxisAlignedBB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0));
			}
			
			if(bX == 1){
				// Well
				if(bZ == 0){
					return Arrays.asList(new AxisAlignedBB(0.3125, 0.5, 0.8125, 0.6875, 0.875, 1.0), new AxisAlignedBB(0.1875, 0, 0.1875, 0.8125, 1.0, 0.8125));
				}
				
				// Pipes
				if(bZ == 1){
					return Arrays.asList(
							new AxisAlignedBB(0.3125, 0.5, 0.0, 0.6875, 0.875, 1.0),
							new AxisAlignedBB(0.0, 0.0, 0.0, 1.0, 0.5, 1.0)
					);
				}
				if(bZ == 2){
					return Arrays.asList(
							new AxisAlignedBB(0.3125, 0.5, 0.0, 0.6875, 0.875, 0.6875),
							new AxisAlignedBB(0.0, 0.5, 0.3125, 1.0, 0.875, 0.6875),
							new AxisAlignedBB(0.0, 0.0, 0.0, 1.0, 0.5, 1.0)
					);
				}
			}
			
			return Arrays.asList(new AxisAlignedBB(0.0, 0.0, 0.0, 1.0, 0.5, 1.0));
		}
		
		return Arrays.asList(new AxisAlignedBB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0));
	}
}
