package com.coobird.staticlogistics.datagen;

import com.coobird.staticlogistics.StaticLogistics;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.concurrent.CompletableFuture;

@Mod.EventBusSubscriber(modid = StaticLogistics.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class SLDataGenerator {
    @SubscribeEvent
    public static void onGatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        ExistingFileHelper helper = event.getExistingFileHelper();
        CompletableFuture<HolderLookup.Provider> lookup = event.getLookupProvider();

        boolean client = event.includeClient();
        generator.addProvider(client, new SLLanguageProvider(output, "en_us"));
        generator.addProvider(client, new SLLanguageProvider(output, "zh_cn"));
        generator.addProvider(client, new SLItemModelProvider(output, helper));

        boolean server = event.includeServer();
        generator.addProvider(server, new VanillaRecipeProvider(output));
        generator.addProvider(server, new SLItemTagsProvider(output, lookup, helper));
    }
}
