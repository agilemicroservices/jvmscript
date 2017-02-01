package org.jvmscript.drools;

import org.jvmscript.file.FileUtility;
import org.kie.api.KieServices;
import org.kie.api.builder.*;
import org.kie.api.event.rule.AgendaEventListener;
import org.kie.api.event.rule.DebugAgendaEventListener;
import org.kie.api.io.Resource;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.internal.io.ResourceFactory;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;

public class DroolsUtility {

    private KieContainer kieContainer;
    private KieSession kieSession;
    private KieFileSystem kieFileSystem;
    private KieServices kieServices;
    private AgendaEventListener agendaEventListener;
    public boolean debug = false;

    public DroolsUtility() {
        kieServices = KieServices.Factory.get();
        kieFileSystem = kieServices.newKieFileSystem();
    }

    @PostConstruct
    public void initialize() {
        kieSession = kieContainer.newKieSession();
    }


    public FactHandle insertFact(Object fact) {
        return kieSession.insert(fact);
    }

    public void deleteFact(FactHandle fact) {
        kieSession.delete(fact);
    }

    public void runRules() {
        if (debug) {
            if (agendaEventListener == null) agendaEventListener = new DebugAgendaEventListener();
            kieSession.addEventListener(agendaEventListener);
        }
        kieSession.fireAllRules();
    }

    public void runRules(boolean debug) {
        this.debug = debug;
        runRules();
    }

    public void runRulesOnce(boolean debug) {
        this.debug = debug;
        runRulesOnce();
    }

    public void runRulesOnce() {
        runRules();
        disposeFacts();
    }

    public void setGlobal(String globalName, Object globalVariable) {
        kieSession.setGlobal(globalName, globalVariable);
    }

    public void initializeFacts() {
        kieSession = kieContainer.newKieSession();
    }

    public void disposeFacts() {
        kieSession.dispose();
        kieSession = kieContainer.newKieSession();
    }

    public void resetRules() {
        kieFileSystem = kieServices.newKieFileSystem();
        kieContainer = null;
    }

    public void addRulesFromFile(String ... drlPaths) throws IOException {
        for (String drlPath: drlPaths) {
            String[] files = FileUtility.dir(drlPath);
            for (String filename : files) {
                kieFileSystem.write(ResourceFactory.newFileResource(new File(filename)));
            }
        }
    }

    public void addRulesFromString(String name, String rules) throws IOException {
        Resource resource = ResourceFactory.newByteArrayResource(rules.getBytes());
        resource.setTargetPath(name);
        kieFileSystem.write(resource);
    }

    public void addRuleFromClassPath(String ... drlFiles) {
    }

    public void buildRules() throws Exception {

        final KieRepository kieRepository = kieServices.getRepository();

        kieRepository.addKieModule(new KieModule() {
            public ReleaseId getReleaseId() {
                return kieRepository.getDefaultReleaseId();
            }
        });

        KieBuilder kieBuilder = kieServices.newKieBuilder(kieFileSystem);
        kieBuilder.buildAll();

        kieContainer =  kieServices.newKieContainer(kieRepository.getDefaultReleaseId());
        kieSession = kieContainer.newKieSession();
    }
}

