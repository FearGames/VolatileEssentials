package it.feargames.volatileessentials.userconf;

import com.earth2me.essentials.EssentialsUserConf;
import me.yamakaja.runtimetransformer.annotation.Inject;
import me.yamakaja.runtimetransformer.annotation.InjectionType;
import me.yamakaja.runtimetransformer.annotation.Transform;

import java.io.File;
import java.util.UUID;

@Transform(EssentialsUserConf.class)
public abstract class EssentialsUserConfModifier extends EssentialsUserConf {

    EssentialsUserConfModifier(String username, UUID uuid, File configFile) {
        super(username, uuid, configFile);
    }

    @Override
    @Inject(InjectionType.OVERRIDE)
    public boolean legacyFileExists() {
        return false;
    }

    @Override
    @Inject(InjectionType.OVERRIDE)
    public void convertLegacyFile() {
    }

    @Override
    @Inject(InjectionType.OVERRIDE)
    public boolean altFileExists() {
        return false;
    }

    @Override
    @Inject(InjectionType.OVERRIDE)
    public void convertAltFile() {
    }
}
