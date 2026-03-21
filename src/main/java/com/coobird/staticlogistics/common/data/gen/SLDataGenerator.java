package com.coobird.staticlogistics.common.data.gen;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.common.data.SLItemModelProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.concurrent.CompletableFuture;

@EventBusSubscriber(modid = Staticlogistics.MODID)
public class SLDataGenerator {
    @SubscribeEvent
    public static void onGatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        ExistingFileHelper helper = event.getExistingFileHelper();
        CompletableFuture<HolderLookup.Provider> lookup = event.getLookupProvider();

        boolean client = event.includeClient();
        generator.addProvider(client, new SlLanguageProvider(output, "en_us"));
        generator.addProvider(client, new SlLanguageProvider(output, "zh_cn"));
        generator.addProvider(client, new SLItemModelProvider(output, helper));

        boolean server = event.includeServer();
        generator.addProvider(server, new VanillaRecipeProvider(output, lookup));
    }
}
