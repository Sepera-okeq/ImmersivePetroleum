package flaxbeard.immersivepetroleum.client;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;

import com.electronwill.nightconfig.core.Config;
import com.mojang.blaze3d.platform.GlStateManager;

import blusunrize.immersiveengineering.api.ManualHelper;
import blusunrize.immersiveengineering.api.multiblocks.ManualElementMultiblock;
import blusunrize.immersiveengineering.client.models.ModelCoresample;
import blusunrize.immersiveengineering.common.blocks.IEBlocks;
import blusunrize.immersiveengineering.common.blocks.metal.MetalScaffoldingType;
import blusunrize.immersiveengineering.common.blocks.multiblocks.IEMultiblocks;
import blusunrize.immersiveengineering.common.gui.GuiHandler;
import blusunrize.immersiveengineering.common.util.ItemNBTHelper;
import blusunrize.lib.manual.ManualElementCrafting;
import blusunrize.lib.manual.ManualElementTable;
import blusunrize.lib.manual.ManualEntry;
import blusunrize.lib.manual.ManualEntry.EntryData;
import blusunrize.lib.manual.ManualInstance;
import blusunrize.lib.manual.TextSplitter;
import blusunrize.lib.manual.Tree.InnerNode;
import flaxbeard.immersivepetroleum.ImmersivePetroleum;
import flaxbeard.immersivepetroleum.api.crafting.DistillationRecipe;
import flaxbeard.immersivepetroleum.api.crafting.PumpjackHandler;
import flaxbeard.immersivepetroleum.api.crafting.PumpjackHandler.ReservoirType;
import flaxbeard.immersivepetroleum.api.energy.FuelHandler;
import flaxbeard.immersivepetroleum.client.gui.DistillationTowerScreen;
import flaxbeard.immersivepetroleum.client.render.AutoLubricatorRenderer;
import flaxbeard.immersivepetroleum.client.render.MultiblockDistillationTowerRenderer;
import flaxbeard.immersivepetroleum.client.render.MultiblockPumpjackRenderer;
import flaxbeard.immersivepetroleum.client.render.SpeedboatRenderer;
import flaxbeard.immersivepetroleum.common.CommonProxy;
import flaxbeard.immersivepetroleum.common.IPConfig;
import flaxbeard.immersivepetroleum.common.IPContent;
import flaxbeard.immersivepetroleum.common.IPContent.Items;
import flaxbeard.immersivepetroleum.common.blocks.metal.AutoLubricatorTileEntity;
import flaxbeard.immersivepetroleum.common.blocks.metal.DistillationTowerTileEntity;
import flaxbeard.immersivepetroleum.common.blocks.metal.PumpjackTileEntity;
import flaxbeard.immersivepetroleum.common.blocks.multiblocks.DistillationTowerMultiblock;
import flaxbeard.immersivepetroleum.common.blocks.multiblocks.PumpjackMultiblock;
import flaxbeard.immersivepetroleum.common.crafting.RecipeReloadListener;
import flaxbeard.immersivepetroleum.common.entity.SpeedboatEntity;
import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.IHasContainer;
import net.minecraft.client.gui.ScreenManager;
import net.minecraft.client.gui.ScreenManager.IScreenFactory;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.model.IBakedModel;
import net.minecraft.client.renderer.model.ModelResourceLocation;
import net.minecraft.client.renderer.tileentity.TileEntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.util.InputMappings;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.ContainerType;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = ImmersivePetroleum.MODID)
public class ClientProxy extends CommonProxy{
	@SuppressWarnings("unused")
	private static final Logger log=LogManager.getLogger(ImmersivePetroleum.MODID+"/ClientProxy");
	public static final String CAT_IP = "ip";
	
	public static final KeyBinding keybind_preview_flip = new KeyBinding("key.immersivepetroleum.projector.flip", InputMappings.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_3, "key.categories.gameplay");
	
	@Override
	public void construct(){}
	
	@Override
	public void setup(){
		RenderingRegistry.registerEntityRenderingHandler(SpeedboatEntity.class, SpeedboatRenderer::new);
	}
	
	@Override
	public void registerContainersAndScreens(){
		super.registerContainersAndScreens();
		
		registerScreen(new ResourceLocation(ImmersivePetroleum.MODID, "distillationtower"), DistillationTowerScreen::new);
	}
	
	@SuppressWarnings("unchecked")
	public <C extends Container, S extends Screen & IHasContainer<C>> void registerScreen(ResourceLocation name, IScreenFactory<C, S> factory){
		ContainerType<C> type=(ContainerType<C>)GuiHandler.getContainerType(name);
		ScreenManager.registerFactory(type, factory);
	}
	
	@Override
	public void completed(){
		
		ManualHelper.addConfigGetter(str->{
			switch(str){
				case "distillationtower_operationcost":{
					return Integer.valueOf((int)(2048 * IPConfig.REFINING.distillationTower_energyModifier.get()));
				}
				case "pumpjack_consumption":{
					return IPConfig.EXTRACTION.pumpjack_consumption.get();
				}
				case "pumpjack_speed":{
					return IPConfig.EXTRACTION.pumpjack_speed.get();
				}
				case "pumpjack_days":{
					int oil_min = 1000000;
					int oil_max = 5000000;
					for(ReservoirType type:PumpjackHandler.reservoirs.values()){
						if(type.name.equals("oil")){
							oil_min = type.minSize;
							oil_max = type.maxSize;
							break;
						}
					}
					
					return Integer.valueOf((((oil_max + oil_min) / 2) + oil_min) / (IPConfig.EXTRACTION.pumpjack_speed.get() * 24000));
				}
				case "autolubricant_speedup":{
					return Double.valueOf(1.25D);
				}
				case "portablegenerator_flux":{
					Map<ResourceLocation, Integer> map = FuelHandler.getFuelFluxesPerTick();
					if(map.size()>0){
						for(ResourceLocation loc:map.keySet()){
							if(loc.toString().contains("gasoline")){
								return map.get(loc);
							}
						}
					}
					
					return Integer.valueOf(-1);
				}
				default:break;
			}
			
			// Last resort
			Config cfg=IPConfig.getRawConfig();
			if(cfg.contains(str)){
				return cfg.get(str);
			}
			return null;
		});
		
		setupManualPages();
	}
	
	@Override
	public void preInit(){
	}
	
	@Override
	public void preInitEnd(){
	}
	
	@Override
	public void init(){
		//ShaderUtil.init(); // Get's initialized befor the first time it's actualy used.
		
		MinecraftForge.EVENT_BUS.register(new ClientEventHandler());
		MinecraftForge.EVENT_BUS.register(new RecipeReloadListener());
		
		keybind_preview_flip.setKeyConflictContext(KeyConflictContext.IN_GAME);
		ClientRegistry.registerKeyBinding(keybind_preview_flip);
	}
	
	/** ImmersivePetroleum's Manual Category */
	private static InnerNode<ResourceLocation, ManualEntry> IP_CATEGORY;
	public void setupManualPages(){
		ManualInstance man=ManualHelper.getManual();
		
		IP_CATEGORY=man.getRoot().getOrCreateSubnode(modLoc("main"), 100);
		
		pumpjack(modLoc("pumpjack"), 0);
		distillation(modLoc("distillationtower"), 1);
		handleReservoirManual(modLoc("reservoir"), 2);
		
		lubricant(modLoc("lubricant"), 3);
		man.addEntry(IP_CATEGORY, modLoc("asphalt"), 4);
		schematics(modLoc("schematics"), 5);
		man.addEntry(IP_CATEGORY, modLoc("speedboat"), 6);
		man.addEntry(IP_CATEGORY, modLoc("napalm"), 7);
		generator(modLoc("portablegenerator"), 8);
		autolube(modLoc("automaticlubricator"), 9);
	}
	
	protected static void autolube(ResourceLocation location, int priority){
		ManualInstance man=ManualHelper.getManual();
		
		ManualEntry.ManualEntryBuilder builder=new ManualEntry.ManualEntryBuilder(man);
		builder.addSpecialElement("automaticlubricator0", 0, new ManualElementCrafting(man, new ItemStack(IPContent.Blocks.auto_lubricator)));
		builder.readFromFile(location);
		man.addEntry(IP_CATEGORY, builder.create(), priority);
	}
	
	protected static void generator(ResourceLocation location, int priority){
		ManualInstance man=ManualHelper.getManual();
		
		ManualEntry.ManualEntryBuilder builder=new ManualEntry.ManualEntryBuilder(man);
		builder.addSpecialElement("portablegenerator0", 0, new ManualElementCrafting(man, new ItemStack(IPContent.Blocks.gas_generator)));
		builder.readFromFile(location);
		man.addEntry(IP_CATEGORY, builder.create(), priority);
	}
	
	protected static void speedboat(ResourceLocation location, int priority){
		ManualInstance man=ManualHelper.getManual();
		
		ManualEntry.ManualEntryBuilder builder=new ManualEntry.ManualEntryBuilder(man);
		builder.addSpecialElement("speedboat0", 0, new ManualElementCrafting(man, new ItemStack(IPContent.Items.speedboat)));
		builder.addSpecialElement("speedboat1", 0, new ManualElementCrafting(man, new ItemStack(IPContent.BoatUpgrades.tank)));
		builder.addSpecialElement("speedboat2", 0, new ManualElementCrafting(man, new ItemStack(IPContent.BoatUpgrades.rudders)));
		builder.addSpecialElement("speedboat3", 0, new ManualElementCrafting(man, new ItemStack(IPContent.BoatUpgrades.ice_breaker)));
		builder.addSpecialElement("speedboat4", 0, new ManualElementCrafting(man, new ItemStack(IPContent.BoatUpgrades.reinforced_hull)));
		builder.addSpecialElement("speedboat5", 0, new ManualElementCrafting(man, new ItemStack(IPContent.BoatUpgrades.paddles)));
		builder.readFromFile(location);
		man.addEntry(IP_CATEGORY, builder.create(), priority);
	}
	
	protected static void lubricant(ResourceLocation location, int priority){
		ManualInstance man=ManualHelper.getManual();
		
		ManualEntry.ManualEntryBuilder builder=new ManualEntry.ManualEntryBuilder(man);
		builder.addSpecialElement("lubricant1", 0, new ManualElementCrafting(man, new ItemStack(IPContent.Items.oil_can)));
		builder.readFromFile(location);
		man.addEntry(IP_CATEGORY, builder.create(), priority);
	}
	
	protected static void pumpjack(ResourceLocation location, int priority){
		ManualInstance man=ManualHelper.getManual();
		
		ManualEntry.ManualEntryBuilder builder=new ManualEntry.ManualEntryBuilder(man);
		builder.addSpecialElement("pumpjack0", 0, new ManualElementMultiblock(man, PumpjackMultiblock.INSTANCE));
		builder.readFromFile(location);
		man.addEntry(IP_CATEGORY, builder.create(), priority);
	}
	
	protected static void distillation(ResourceLocation location, int priority){
		ManualInstance man=ManualHelper.getManual();
		
		ManualEntry.ManualEntryBuilder builder=new ManualEntry.ManualEntryBuilder(man);
		builder.addSpecialElement("distillationtower0", 0, new ManualElementMultiblock(man, DistillationTowerMultiblock.INSTANCE));
		builder.addSpecialElement("distillationtower1", 0, ()->{
			Collection<DistillationRecipe> recipeList = DistillationRecipe.recipes.values();
			List<ITextComponent[]> l = new ArrayList<ITextComponent[]>();
			for(DistillationRecipe recipe:recipeList){
				boolean first = true;
				for(FluidStack output:recipe.fluidOutput){
					String inputName = recipe.input.getMatchingFluidStacks().get(0).getDisplayName().getUnformattedComponentText();
					String outputName = output.getDisplayName().getUnformattedComponentText();
					ITextComponent[] array = new ITextComponent[]{
							new StringTextComponent(first ? recipe.input.getAmount()+"mB "+inputName : ""),
							new StringTextComponent(output.getAmount()+"mB "+outputName)
					};
					l.add(array);
					first = false;
				}
			}
			
			return new ManualElementTable(man, l.toArray(new ITextComponent[0][]), false);
		});
		builder.readFromFile(location);
		man.addEntry(IP_CATEGORY, builder.create(), priority);
	}
	
	protected static void schematics(ResourceLocation location, int priority){
		ManualInstance man=ManualHelper.getManual();
		
		ItemStack projectorWithNBT=new ItemStack(Items.projector);
		ItemNBTHelper.putString(projectorWithNBT, "multiblock", IEMultiblocks.ARC_FURNACE.getUniqueName().toString());
		
		ManualEntry.ManualEntryBuilder builder=new ManualEntry.ManualEntryBuilder(man);
		builder.addSpecialElement("schematics0", 0, new ManualElementCrafting(man, new ItemStack(Items.projector)));
		builder.addSpecialElement("schematics1", 0, new ManualElementCrafting(man, projectorWithNBT));
		builder.readFromFile(location);
		man.addEntry(IP_CATEGORY, builder.create(), priority);
	}
	
	protected static void handleReservoirManual(ResourceLocation location, int priority){
		ManualInstance man=ManualHelper.getManual();
		
		ManualEntry.ManualEntryBuilder builder=new ManualEntry.ManualEntryBuilder(man);
		builder.setContent(ClientProxy::createContent);
		builder.setLocation(location);
		man.addEntry(IP_CATEGORY, builder.create(), priority);
	}
	
	protected static EntryData createContentTest(TextSplitter splitter){
		return new EntryData("title", "subtext", "content");
	}
	
	static final DecimalFormat FORMATTER = new DecimalFormat("#,###.##");
	static ManualEntry entry;
	protected static EntryData createContent(TextSplitter splitter){
		ArrayList<ItemStack> list = new ArrayList<>();
		final ReservoirType[] reservoirs = PumpjackHandler.reservoirs.values().toArray(new ReservoirType[0]);
		
		StringBuilder contentBuilder=new StringBuilder();
		contentBuilder.append(I18n.format("ie.manual.entry.reservoirs.oil0"));
		contentBuilder.append(I18n.format("ie.manual.entry.reservoirs.oil1"));
		
		for(int i=0;i<reservoirs.length;i++){
			ReservoirType type=reservoirs[i];
			
			ImmersivePetroleum.log.info("Creating entry for "+type);
			
			String name = "desc.immersivepetroleum.info.reservoir." + type.name;
			String localizedName = I18n.format(name);
			if(localizedName.equalsIgnoreCase(name))
				localizedName = type.name;
			
			char c=localizedName.toLowerCase().charAt(0);
			boolean isVowel = (c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u');
			String aOrAn = I18n.format(isVowel ? "ie.manual.entry.reservoirs.vowel" : "ie.manual.entry.reservoirs.consonant");
			
			String dimBLWL = "";
			if(type.dimWhitelist != null && type.dimWhitelist.size() > 0){
				String validDims = "";
				for(ResourceLocation rl:type.dimWhitelist){
					validDims += (!validDims.isEmpty() ? ", " : "") + "<dim;" + rl + ">";
				}
				dimBLWL = I18n.format("ie.manual.entry.reservoirs.dim.valid", localizedName, validDims, aOrAn);
			}else if(type.dimBlacklist != null && type.dimBlacklist.size() > 0){
				String invalidDims = "";
				for(ResourceLocation rl:type.dimBlacklist){
					invalidDims += (!invalidDims.isEmpty() ? ", " : "") + "<dim;" + rl + ">";
				}
				dimBLWL = I18n.format("ie.manual.entry.reservoirs.dim.invalid", localizedName, invalidDims, aOrAn);
			}else{
				dimBLWL = I18n.format("ie.manual.entry.reservoirs.dim.any", localizedName, aOrAn);
			}
			
			String bioBLWL = "";
			if(type.bioWhitelist != null && type.bioWhitelist.size() > 0){
				String validBiomes = "";
				for(ResourceLocation rl:type.bioWhitelist){
					Biome bio=ForgeRegistries.BIOMES.getValue(rl);
					validBiomes += (!validBiomes.isEmpty() ? ", " : "") + (bio != null ? bio.getDisplayName().getFormattedText() : rl);
				}
				bioBLWL = I18n.format("ie.manual.entry.reservoirs.bio.valid", validBiomes);
			}else if(type.bioBlacklist != null && type.bioBlacklist.size() > 0){
				String invalidBiomes = "";
				for(ResourceLocation rl:type.bioBlacklist){
					Biome bio=ForgeRegistries.BIOMES.getValue(rl);
					invalidBiomes += (!invalidBiomes.isEmpty() ? ", " : "") + (bio != null ? bio.getDisplayName().getFormattedText() : rl);
				}
				bioBLWL = I18n.format("ie.manual.entry.reservoirs.bio.invalid", invalidBiomes);
			}else{
				bioBLWL = I18n.format("ie.manual.entry.reservoirs.bio.any");
			}
			
			String fluidName = "";
			Fluid fluid = type.getFluid();
			if(fluid != null){
				fluidName = new FluidStack(fluid, 1).getDisplayName().getUnformattedComponentText();
			}
			
			String repRate = "";
			if(type.replenishRate > 0){
				repRate = I18n.format("ie.manual.entry.reservoirs.replenish", type.replenishRate, fluidName);
			}
			contentBuilder.append(I18n.format("ie.manual.entry.reservoirs.content", dimBLWL, fluidName, FORMATTER.format(type.minSize), FORMATTER.format(type.maxSize), repRate, bioBLWL));
			
			if(i<(reservoirs.length-1))
				contentBuilder.append("<np>");
			
			list.add(new ItemStack(fluid.getFilledBucket()));
		}
		
		// This no longer works, there's no way to do this legit!
		/*
		ManualElementItem[] items=pages.toArray(new ManualElementItem[list.size()]);
		pages.toArray(new ManualElementItem[pages.size()])
		if(resEntry != null){
			resEntry.setPages(items);
		}else{
			resEntry = man.addEntry(ipCat, modLoc("oil"), ep++);
		}
		*/
		
		String translatedTitle=I18n.format("ie.manual.entry.reservoirs.title");
		String tanslatedSubtext=I18n.format("ie.manual.entry.reservoirs.subtitle");
		String formattedContent=contentBuilder.toString().replaceAll("\r\n|\r|\n", "\n");
		return new EntryData(translatedTitle, tanslatedSubtext, formattedContent);
	}
	
	@Override
	public void postInit(){
		ClientRegistry.bindTileEntitySpecialRenderer(DistillationTowerTileEntity.class, new MultiblockDistillationTowerRenderer());
		ClientRegistry.bindTileEntitySpecialRenderer(PumpjackTileEntity.class, new MultiblockPumpjackRenderer());
		ClientRegistry.bindTileEntitySpecialRenderer(AutoLubricatorTileEntity.class, new AutoLubricatorRenderer());
	}
	
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onModelBakeEvent(ModelBakeEvent event){
		ModelResourceLocation mLoc = new ModelResourceLocation(IEBlocks.StoneDecoration.coresample.getRegistryName(), "inventory");
		IBakedModel model=event.getModelRegistry().get(mLoc);
		if(model instanceof ModelCoresample){
			//event.getModelRegistry().put(mLoc, new ModelCoresampleExtended());
		}
	}
	
	public void renderTile(TileEntity te){
		
		if(te instanceof PumpjackTileEntity){
			GlStateManager.pushMatrix();
			GlStateManager.rotatef(-90, 0, 1, 0);
			GlStateManager.translatef(1, 1, -2);
			
			float pt = 0;
			if(Minecraft.getInstance().player != null){
				((PumpjackTileEntity) te).activeTicks = Minecraft.getInstance().player.ticksExisted;
				pt = Minecraft.getInstance().getRenderPartialTicks();
			}
			
			TileEntityRenderer<TileEntity> tesr = TileEntityRendererDispatcher.instance.getRenderer((TileEntity) te);
			
			tesr.render((TileEntity) te, 0, 0, 0, pt, 0);
			GlStateManager.popMatrix();
		}else{
			GlStateManager.pushMatrix();
			GlStateManager.rotatef(-90, 0, 1, 0);
			GlStateManager.translatef(0, 1, -4);
			
			TileEntityRenderer<TileEntity> tesr = TileEntityRendererDispatcher.instance.getRenderer((TileEntity) te);
			
			tesr.render((TileEntity) te, 0, 0, 0, 0, 0);
			GlStateManager.popMatrix();
		}
	}

	@Override
	public void drawUpperHalfSlab(ItemStack stack){
		
		// Render slabs on top half
		BlockRendererDispatcher blockRenderer = Minecraft.getInstance().getBlockRendererDispatcher();
		BlockState state = IEBlocks.MetalDecoration.steelScaffolding.get(MetalScaffoldingType.STANDARD).getDefaultState();
		IBakedModel model = blockRenderer.getBlockModelShapes().getModel(state);
		
		GlStateManager.pushMatrix();
		GlStateManager.translatef(0.0F, 0.5F, 1.0F);
		RenderHelper.disableStandardItemLighting();
		GlStateManager.blendFunc(770, 771);
		GlStateManager.enableBlend();
		GlStateManager.disableCull();
		if(Minecraft.isAmbientOcclusionEnabled()){
			GlStateManager.shadeModel(7425);
		}else{
			GlStateManager.shadeModel(7424);
		}
		
		blockRenderer.getBlockModelRenderer().renderModelBrightness(model, state, 0.75F, false);
		GlStateManager.popMatrix();
	}
	
	@Override
	public World getClientWorld(){
		return Minecraft.getInstance().world;
	}
	
	@Override
	public PlayerEntity getClientPlayer(){
		return Minecraft.getInstance().player;
	}
}