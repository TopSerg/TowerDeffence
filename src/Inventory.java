import java.util.Map;

public class Inventory {
    private Map<ResourceType, Integer> resources;
    int size, resValue = 0;

    public Inventory(int size){
        this.size = size;
    }

    public boolean addItem(Resource resource){
        if (resValue + resource.getAmount() < size){
            resources.put(resource.getType(), resource.getAmount());
            return true;
        }
        return false;
    }

    public Map<ResourceType, Integer> getResources(){
        return resources;
    }

    public int getSize() {
        return size;
    }
}
