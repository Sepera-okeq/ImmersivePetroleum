package flaxbeard.immersivepetroleum.api.crafting.reservoir;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ColumnPos;
import net.minecraftforge.common.util.Lazy;

public class ReservoirVein{
	final ColumnPos pos;
	final ResourceLocation name;
	final Lazy<Reservoir> reservoir;
	public ReservoirVein(ColumnPos pos, ResourceLocation name){
		this.pos = pos;
		this.name = name;
		this.reservoir = Lazy.of(() -> Reservoir.map.get(name));
	}
	
	public ColumnPos getPos(){
		return this.pos;
	}
	
	public Reservoir getReservoir(){
		return this.reservoir.get();
	}
	
	public CompoundTag writeToNBT(){
		CompoundTag nbt = new CompoundTag();
		
		nbt.putInt("x", this.pos.x);
		nbt.putInt("z", this.pos.z);
		nbt.putString("name", this.name.toString());
		
		return nbt;
	}
}