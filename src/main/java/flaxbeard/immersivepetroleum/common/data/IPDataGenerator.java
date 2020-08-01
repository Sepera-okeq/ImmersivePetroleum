package flaxbeard.immersivepetroleum.common.data;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import flaxbeard.immersivepetroleum.ImmersivePetroleum;
import net.minecraft.data.DataGenerator;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.fml.event.lifecycle.GatherDataEvent;

@EventBusSubscriber(modid=ImmersivePetroleum.MODID, bus=Bus.MOD)
public class IPDataGenerator{
	public static final Logger log=LogManager.getLogger(ImmersivePetroleum.MODID+"/DataGenerator");
	
	@SubscribeEvent
	public static void generate(GatherDataEvent event){
		DataGenerator generator=event.getGenerator();
		
		generator.addProvider(new IPBlockTags(generator));
		generator.addProvider(new IPItemTags(generator));
		generator.addProvider(new IPRecipes(generator));
	}
}