package kcemu.env;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.TypeVariable;

import org.mockito.Mockito;
import org.mockito.internal.stubbing.defaultanswers.ReturnsEmptyValues;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * Platzhalter für Server-Objekte, die im Emulator keine eigene Logik brauchen.
 *
 * Verhalten eines nicht ausdrücklich belegten Aufrufs:
 * <ul>
 *   <li>Default-Methoden eines Interfaces laufen echt (wie bei einer echten Implementierung),</li>
 *   <li>Collections/Streams/Optional sind leer, Zahlen 0, boolean false, Strings null,</li>
 *   <li>Datenobjekte (…Model, z. B. UserModel) sind null – wie eine leere Datenbank („nicht gefunden“),</li>
 *   <li>andere Keycloak-Interfaces liefern wieder einen solchen Platzhalter.</li>
 * </ul>
 */
public final class Mocks {

    private Mocks() {
    }

    private static final ReturnsEmptyValues EMPTY = new ReturnsEmptyValues();

    public static final Answer<Object> DEFAULTS = new Answer<>() {
        @Override
        public Object answer(InvocationOnMock inv) throws Throwable {
            Method m = inv.getMethod();
            if (m.isDefault()) {
                return inv.callRealMethod();
            }
            Object v = EMPTY.answer(inv);
            if (v != null) {
                return v;
            }
            if (m.getGenericReturnType() instanceof TypeVariable) {
                return null;
            }
            Class<?> rt = m.getReturnType();
            if (rt.getSimpleName().endsWith("Model")) {
                return null;
            }
            if (rt.isInterface() && !rt.getName().startsWith("java.") && !Modifier.isFinal(rt.getModifiers())) {
                return mock(rt);
            }
            return null;
        }
    };

    public static <T> T mock(Class<T> type) {
        return Mockito.mock(type, Mockito.withSettings().defaultAnswer(DEFAULTS).stubOnly());
    }
}
