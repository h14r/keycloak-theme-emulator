package kcemu.env;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

import org.keycloak.theme.FreeMarkerException;
import org.keycloak.theme.KeycloakSanitizerMethod;
import org.keycloak.theme.Theme;
import org.keycloak.theme.freemarker.DefaultFreeMarkerProvider;
import org.keycloak.theme.freemarker.FreeMarkerProvider;

import freemarker.template.TemplateMethodModelEx;
import freemarker.template.TemplateModelException;

/**
 * Leitet jeden Aufruf unverändert an Keycloaks {@link DefaultFreeMarkerProvider} weiter
 * (gleiche FreeMarker-Konfiguration, gleicher Template-Loader, gleiches kcSanitize).
 *
 * Zusätzlich – ohne das Ergebnis zu verändern – wird protokolliert:
 * welche Template-Variablen bereitstanden, welche Textschlüssel abgefragt wurden und
 * welche davon im Nachrichtenbündel fehlten, sowie Template-Fehler.
 */
public class InstrumentedFreeMarker implements FreeMarkerProvider {

    /** Ohne Template-Cache – entspricht --spi-theme--cache-templates=false (Entwicklungsmodus). */
    private final DefaultFreeMarkerProvider delegate = new DefaultFreeMarkerProvider(null, new KeycloakSanitizerMethod());

    public final List<Run> runs = new ArrayList<>();

    public static class Run {
        public String template;
        public String themeName;
        public List<String> dataModel = new ArrayList<>();
        public Set<String> usedKeys = new LinkedHashSet<>();
        public Set<String> missingKeys = new LinkedHashSet<>();
        public Map<String, String> usedTexts = new TreeMap<>();
        public Throwable error;
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public String processTemplate(Object data, String templateName, Theme theme) throws FreeMarkerException {
        Run run = new Run();
        run.template = templateName;
        run.themeName = theme.getName();
        runs.add(run);
        if (data instanceof Map map) {
            run.dataModel.addAll(new TreeMap<>(map).keySet());
            wrap(map, "msg", run, false);
            wrap(map, "advancedMsg", run, true);
        }
        try {
            return delegate.processTemplate(data, templateName, theme);
        } catch (FreeMarkerException e) {
            run.error = e.getCause() != null ? e.getCause() : e;
            throw e;
        } finally {
            if (data instanceof Map map) {
                unwrap(map, "msg");
                unwrap(map, "advancedMsg");
            }
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void wrap(Map map, String name, Run run, boolean advanced) {
        Object m = map.get(name);
        if (m instanceof TemplateMethodModelEx original && !(m instanceof Recording)) {
            map.put(name, new Recording(original, bundleOf(original), run, advanced));
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void unwrap(Map map, String name) {
        if (map.get(name) instanceof Recording r) {
            map.put(name, r.original);
        }
    }

    private static Properties bundleOf(Object method) {
        for (Class<?> c = method.getClass(); c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Properties.class.isAssignableFrom(f.getType())) {
                    try {
                        f.setAccessible(true);
                        return (Properties) f.get(method);
                    } catch (ReflectiveOperationException e) {
                        return null;
                    }
                }
            }
        }
        return null;
    }

    static final class Recording implements TemplateMethodModelEx {
        final TemplateMethodModelEx original;
        final Properties bundle;
        final Run run;
        final boolean advanced;

        Recording(TemplateMethodModelEx original, Properties bundle, Run run, boolean advanced) {
            this.original = original;
            this.bundle = bundle;
            this.run = run;
            this.advanced = advanced;
        }

        @Override
        @SuppressWarnings("rawtypes")
        public Object exec(List args) throws TemplateModelException {
            Object result = original.exec(args);
            if (!args.isEmpty() && bundle != null) {
                String key = args.get(0).toString();
                if (advanced) {
                    if (key.startsWith("${") && key.endsWith("}")) {
                        key = key.substring(2, key.length() - 1);
                    } else {
                        key = null;
                    }
                }
                if (key != null) {
                    run.usedKeys.add(key);
                    if (!bundle.containsKey(key)) {
                        run.missingKeys.add(key);
                    } else if (result != null) {
                        run.usedTexts.put(key, result.toString());
                    }
                }
            }
            return result;
        }
    }

    public Map<String, Object> summary() {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Run r : runs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("template", r.template);
            m.put("theme", r.themeName);
            m.put("dataModel", r.dataModel);
            m.put("usedKeys", r.usedKeys);
            m.put("missingKeys", r.missingKeys);
            if (r.error != null) {
                m.put("error", String.valueOf(r.error.getMessage()));
            }
            list.add(m);
        }
        out.put("templates", list);
        return out;
    }

    @Override
    public void close() {
    }
}
