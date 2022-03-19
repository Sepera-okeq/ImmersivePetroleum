package flaxbeard.immersivepetroleum.common.blocks.stone;

import java.util.function.Supplier;

import flaxbeard.immersivepetroleum.common.IPTileTypes;
import flaxbeard.immersivepetroleum.common.blocks.IPBlockBase;
import flaxbeard.immersivepetroleum.common.blocks.tileentities.WellTileEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Material;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class WellBlock extends IPBlockBase implements EntityBlock{
	public WellBlock(){
		super(Block.Properties.of(Material.STONE).strength(-1.0F, 3600000.0F).noDrops().isValidSpawn((s, r, p, e) -> false));
	}
	
	@Override
	public Supplier<BlockItem> blockItemSupplier(){
		throw new UnsupportedOperationException();
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pPos, BlockState pState){
		WellTileEntity tile = IPTileTypes.WELL.get().create(pPos, pState);
		return tile;
	}

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            @Nonnull Level level, @Nonnull BlockState state, @Nonnull BlockEntityType<T> type
    ){
        return createTickerHelper(type, IPTileTypes.WELL);
    }
}
