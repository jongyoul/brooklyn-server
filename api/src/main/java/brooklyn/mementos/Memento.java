package brooklyn.mementos;

import java.io.Serializable;
import java.util.Map;

public interface Memento extends Serializable {
    
    String getId();
    
    public String getType();
    
    public String getDisplayName();
    
    /**
     * A (weakly-typed) property set for this memento.
     * These can be used to avoid sub-classing the entity memento, but developers can sub-class to get strong typing if desired.
     */
    public Object getCustomProperty(String name);

    public Map<String, ? extends Object> getCustomProperties();
}
