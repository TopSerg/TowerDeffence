package towerdefence.roadmap;

import towerdefence.building.*;
import towerdefence.combat.*;
import towerdefence.game.GameState;
import towerdefence.resource.*;
import towerdefence.unit.*;
import towerdefence.world.*;

import java.awt.*;
import java.util.*;
import java.util.List;

/** Experimental, deliberately compact implementation of the complete current roadmap. */
public final class RoadmapRuntime {
    public enum BlueprintPhase { PLANNED, MARKING, WAITING_FOR_BUILDER, WAITING_FOR_RESOURCES, BUILDING, DONE, DAMAGED }

    public enum FacilityKind {
        POWER_PLANT("Энергоблок",95,32,0), CHEMICAL_PLANT("Химический комплекс",105,22,0),
        FOUNDRY("Металлургический цех",82,24,0), ROBOTICS_FACILITY("Робототехнический цех",110,18,6);
        private final String name; private final int metal,coal,components;
        FacilityKind(String n,int m,int c,int p){name=n;metal=m;coal=c;components=p;}
        public String getDisplayName(){return name;}
        public Map<ResourceType,Integer> getCost(){
            EnumMap<ResourceType,Integer> r=new EnumMap<>(ResourceType.class);
            if(metal>0)r.put(ResourceType.METAL,metal); if(coal>0)r.put(ResourceType.COAL,coal);
            if(components>0)r.put(ResourceType.COMPONENT,components); return Collections.unmodifiableMap(r);
        }
    }

    public enum MachineType {
        AMMO_PRESS("Ammo Press",in(ResourceType.METAL,1,ResourceType.COAL,1),ResourceType.AMMO,10,90,8,0),
        STABILIZER_I_ASSEMBLER("Stabilizer I",in(ResourceType.METAL,18,ResourceType.SCRAP,4),ResourceType.STABILIZER_I,1,150,7,0),
        COOLING_ASSEMBLER("Cooling Module",in(ResourceType.METAL,24,ResourceType.SCRAP,6),ResourceType.COOLING_MODULE,1,180,9,0),
        STABILIZER_II_ASSEMBLER("Stabilizer II",in(ResourceType.METAL,34,ResourceType.SCRAP,9),ResourceType.STABILIZER_II,1,210,11,0),
        FOUNDRY("Foundry / Plate",in(ResourceType.METAL,2),ResourceType.PLATE,1,80,10,0),
        BEAM_ROLLER("Beam Roller",in(ResourceType.METAL,3),ResourceType.BEAM,1,105,11,0),
        ALLOY_FURNACE("Alloy Furnace",in(ResourceType.METAL,2,ResourceType.COAL,1),ResourceType.ALLOY,1,130,13,0),
        COMPONENT_ASSEMBLER("Components",in(ResourceType.PLATE,2,ResourceType.SCRAP,1),ResourceType.COMPONENT,1,120,9,0),
        GENERATOR("Generator",in(ResourceType.COAL,1),null,0,1,0,40),
        CHEMICAL_REACTOR("Coolant Reactor",in(ResourceType.OIL,1),ResourceType.COOLANT,2,135,12,0),
        FUEL_REFINERY("Fuel Refinery",in(ResourceType.OIL,2),ResourceType.FUEL,2,120,10,0),
        LUBRICANT_REACTOR("Lubricant Reactor",in(ResourceType.OIL,1,ResourceType.ORGANIC,1),ResourceType.LUBRICANT,2,150,10,0),
        EXPLOSIVES_REACTOR("Explosives Reactor",in(ResourceType.OIL,1,ResourceType.COAL,1),ResourceType.EXPLOSIVES,1,180,14,0),
        ROBOT_ASSEMBLER("Robotics",in(ResourceType.COMPONENT,3,ResourceType.METAL,2),ResourceType.ROBOT_KIT,1,240,14,0),
        STORAGE("Storage",Collections.emptyMap(),null,0,1,1,0), SORTER("Sorter",Collections.emptyMap(),null,0,1,2,0),
        ROUTER("Router",Collections.emptyMap(),null,0,1,2,0), PRIORITY_GATE("Priority",Collections.emptyMap(),null,0,1,2,0),
        OVERFLOW_GATE("Overflow",Collections.emptyMap(),null,0,1,2,0), BALANCER("Balancer",Collections.emptyMap(),null,0,1,3,0);
        private final String name; private final Map<ResourceType,Integer> inputs; private final ResourceType output;
        private final int amount,ticks,power,generation;
        MachineType(String n,Map<ResourceType,Integer> i,ResourceType o,int a,int t,int p,int g){name=n;inputs=i;output=o;amount=a;ticks=t;power=p;generation=g;}
        public String getDisplayName(){return name;} public Map<ResourceType,Integer> getInputs(){return inputs;}
        public ResourceType getOutput(){return output;} public int getOutputAmount(){return amount;} public int getWorkTicks(){return ticks;}
        public int getPowerDemand(){return power;} public int getGeneration(){return generation;}
        private static Map<ResourceType,Integer> in(Object... p){EnumMap<ResourceType,Integer> r=new EnumMap<>(ResourceType.class);for(int i=0;i+1<p.length;i+=2)r.put((ResourceType)p[i],(Integer)p[i+1]);return Collections.unmodifiableMap(r);}
    }

    public static final class Alert { private final String text; private final Tile tile; public Alert(String t,Tile p){text=t;tile=p;} public String getText(){return text;} public Tile getTile(){return tile;} }

    public static final class FacilityBlueprint extends Building {
        private final FacilityKind kind; private final Inventory delivered=new Inventory(256); private int progress;
        public FacilityBlueprint(Tile p,FacilityKind k){super(120,0,p,new Color(90,185,225));type=BuildingType.CONSTRUCTION_SITE;kind=k;}
        public FacilityKind getKind(){return kind;} public Map<ResourceType,Integer> getRequired(){return kind.getCost();}
        public Inventory getDelivered(){return delivered;} public int getRemaining(ResourceType t){return Math.max(0,getRequired().getOrDefault(t,0)-delivered.getAmount(t));}
        public boolean hasAllMaterials(){for(Map.Entry<ResourceType,Integer>e:getRequired().entrySet())if(!delivered.has(e.getKey(),e.getValue()))return false;return true;}
        public int deliverFrom(Inventory src){int n=0;for(ResourceType t:ResourceType.values())n+=src.transferTo(delivered,t,getRemaining(t));return n;}
        public void work(int n){if(n>0&&hasAllMaterials())progress=Math.min(420,progress+n);} public boolean isComplete(){return progress>=420;}
        public double getProgressFraction(){return progress/420.0;} @Override public int getFootprintWidth(){return 3;} @Override public int getFootprintHeight(){return 3;} @Override public void update(){}
        @Override public void render(Graphics g,int s){if(position==null)return;g.setColor(new Color(70,165,225,90));g.fillRect(position.getX()*s+3,position.getY()*s+3,3*s-6,3*s-6);g.setColor(Color.WHITE);g.drawString(kind.getDisplayName(),position.getX()*s+7,position.getY()*s+17);}
    }

    public static final class FacilityBuilding extends Workshop {
        private final FacilityKind kind; public FacilityBuilding(Tile p,FacilityKind k){super(p);kind=k;} public FacilityKind getKind(){return kind;}
        @Override public void render(Graphics g,int s){super.render(g,s);if(getPosition()!=null){g.setColor(new Color(245,235,170));g.drawString(kind.getDisplayName(),getPosition().getX()*s+8,getPosition().getY()*s+2*s+18);}}
    }

    public static final class InteriorBuildTask {
        private final int x,y; private final Direction direction; private final MachineType machineType; private final boolean conveyor;
        private BlueprintPhase phase=BlueprintPhase.PLANNED; private int mark,build;
        InteriorBuildTask(int x,int y,Direction d,MachineType m,boolean c){this.x=x;this.y=y;direction=d==null?Direction.RIGHT:d;machineType=m;conveyor=c;}
        public int getX(){return x;} public int getY(){return y;} public Direction getDirection(){return direction;}
        public MachineType getMachineType(){return machineType;} public boolean isConveyor(){return conveyor;} public BlueprintPhase getPhase(){return phase;}
    }

    public static final class InteriorMachine {
        private final MachineType type; private final int x,y; private final Direction out; private final Inventory input=new Inventory(96);
        private final Deque<ResourceType> outputs=new ArrayDeque<>(); private int progress,damage,age,fuel;
        InteriorMachine(MachineType t,int x,int y,Direction d){type=t;this.x=x;this.y=y;out=d==null?Direction.RIGHT:d;}
        public MachineType getType(){return type;} public int getX(){return x;} public int getY(){return y;} public Direction getOutputDirection(){return out;}
        public int getDamage(){return damage;} public boolean isDestroyed(){return damage>=100;} public int getPowerDemand(){return isDestroyed()?0:type.getPowerDemand();}
        public int getGeneration(){return !isDestroyed()&&type==MachineType.GENERATOR&&fuel>0?type.getGeneration():0;}
        public void damage(int n){damage=Math.min(100,damage+Math.max(0,n));} public void repair(int n){damage=Math.max(0,damage-Math.max(0,n));}
        public boolean accepts(ResourceType r){if(r==null||isDestroyed())return false;if(isLogistics())return input.getFreeSpace()>0;return type.getInputs().containsKey(r)&&input.getAmount(r)<Math.max(4,type.getInputs().get(r)*4);}
        public boolean accept(ResourceType r){return accepts(r)&&input.add(r,1);} public ResourceType peekOutput(){return outputs.peekFirst();} public ResourceType takeOutput(){return outputs.pollFirst();}
        private boolean isLogistics(){return type.ordinal()>=MachineType.STORAGE.ordinal();}
        void update(FactoryState f){
            age++;if(isDestroyed())return;
            if(type==MachineType.GENERATOR){if(fuel>0){fuel--;return;}if(input.remove(ResourceType.COAL,1))fuel=360;return;}
            if(isLogistics()){if(type!=MachineType.STORAGE&&outputs.size()<8)for(ResourceType r:ResourceType.values())if(input.remove(r,1)){outputs.add(r);break;}return;}
            if(!f.isPowerAvailable()||!hasInputs(f)){progress=0;return;} if(type==MachineType.CHEMICAL_REACTOR&&f.getFluid(ResourceType.WATER)<=0){progress=0;return;}
            if(age%(1+damage/25)!=0)return;if(++progress<type.getWorkTicks())return;progress=0;consume(f);
            if(type==MachineType.CHEMICAL_REACTOR){f.removeFluid(ResourceType.WATER,1);f.addFluid(ResourceType.COOLANT,type.getOutputAmount());return;}
            if(type.getOutput()!=null&&type.getOutput().isLiquid()){f.addFluid(type.getOutput(),type.getOutputAmount());return;}
            if(type.getOutput()!=null)for(int i=0;i<type.getOutputAmount();i++)outputs.add(type.getOutput());
        }
        private boolean hasInputs(FactoryState f){for(Map.Entry<ResourceType,Integer>e:type.getInputs().entrySet()){if(e.getKey().isLiquid()){if(f.getFluid(e.getKey())<e.getValue())return false;}else if(!input.has(e.getKey(),e.getValue()))return false;}return true;}
        private void consume(FactoryState f){for(Map.Entry<ResourceType,Integer>e:type.getInputs().entrySet())if(e.getKey().isLiquid())f.removeFluid(e.getKey(),e.getValue());else input.remove(e.getKey(),e.getValue());}
    }

    public final class FactoryState {
        private final Workshop workshop; private final InteriorMachine[][] machines=new InteriorMachine[9][9]; private final List<InteriorBuildTask> tasks=new ArrayList<>();
        private final EnumMap<ResourceType,Integer> fluids=new EnumMap<>(ResourceType.class); private int bots=1,repairTick,lastHealth; private boolean powered,wasRuined;
        FactoryState(Workshop w){workshop=w;lastHealth=w.getHealth();}
        public Workshop getWorkshop(){return workshop;} public int getInternalBots(){return bots;} public void addInternalBot(){bots++;}
        public boolean isPowerAvailable(){return powered||getGeneration()>0;} public void setPowerAvailable(boolean v){powered=v;}
        public List<InteriorBuildTask> getTasks(){return Collections.unmodifiableList(tasks);} public List<InteriorMachine> getMachines(){List<InteriorMachine>r=new ArrayList<>();for(InteriorMachine[]row:machines)for(InteriorMachine m:row)if(m!=null)r.add(m);return Collections.unmodifiableList(r);}
        public InteriorMachine getMachine(int x,int y){return x<0||y<0||x>=9||y>=9?null:machines[y][x];}
        public int getPowerDemand(){int n=0;for(InteriorMachine m:getMachines())n+=m.getPowerDemand();return n;} public int getGeneration(){int n=0;for(InteriorMachine m:getMachines())n+=m.getGeneration();return n;}
        public int getFluid(ResourceType r){return fluids.getOrDefault(r,0);} public void addFluid(ResourceType r,int n){if(r!=null&&n>0)fluids.merge(r,n,Integer::sum);} public int removeFluid(ResourceType r,int n){int h=getFluid(r),v=Math.min(h,Math.max(0,n));if(v>0)fluids.put(r,h-v);return v;}
        public boolean queueConveyor(int x,int y,Direction d){if(!reserve(x,y))return false;tasks.add(new InteriorBuildTask(x,y,d,null,true));return true;}
        public boolean queueMachine(int x,int y,MachineType t,Direction d){if(t==null||!reserve(x,y))return false;tasks.add(new InteriorBuildTask(x,y,d,t,false));return true;}
        private boolean reserve(int x,int y){return getMachine(x,y)==null&&workshop.reserveInteriorCell(x,y);}
        void markOne(){for(InteriorBuildTask t:tasks)if(t.phase==BlueprintPhase.PLANNED||t.phase==BlueprintPhase.MARKING){t.phase=BlueprintPhase.MARKING;if(++t.mark>=30)t.phase=BlueprintPhase.WAITING_FOR_BUILDER;return;}}
        void update(){
            detectHit();if(workshop.isRuined()){if(!wasRuined)destroyAll();wasRuined=true;return;}wasRuined=false;
            int cap=bots;for(InteriorBuildTask t:tasks){if(cap<=0)break;if(t.phase!=BlueprintPhase.WAITING_FOR_BUILDER&&t.phase!=BlueprintPhase.BUILDING)continue;t.phase=BlueprintPhase.BUILDING;cap--;if(++t.build<(t.conveyor?35:90))continue;workshop.releaseInteriorReservation(t.x,t.y);boolean ok=t.conveyor?workshop.placeInteriorConveyor(t.x,t.y,t.direction):buildMachine(t);t.phase=ok?BlueprintPhase.DONE:BlueprintPhase.DAMAGED;}
            ingest();for(InteriorMachine m:getMachines())m.update(this);emit();repair();
        }
        private boolean buildMachine(InteriorBuildTask t){if(getMachine(t.x,t.y)!=null)return false;machines[t.y][t.x]=new InteriorMachine(t.machineType,t.x,t.y,t.direction);return true;}
        private void ingest(){for(WorkshopItem item:new ArrayList<>(workshop.getInteriorItems())){InteriorConveyor c=workshop.getInteriorConveyor(item.getX(),item.getY());if(c==null)continue;int nx=item.getX()+c.getDirection().getDx(),ny=item.getY()+c.getDirection().getDy();InteriorMachine m=getMachine(nx,ny);if(m!=null&&m.accept(item.getType()))workshop.removeInteriorItem(item);}}
        private void emit(){for(InteriorMachine m:getMachines()){ResourceType r=m.peekOutput();if(r==null)continue;int x=m.getX()+m.getOutputDirection().getDx(),y=m.getY()+m.getOutputDirection().getDy();if(workshop.spawnInteriorItem(r,x,y))m.takeOutput();}}
        private void detectHit(){int now=workshop.getHealth();if(now<lastHealth){int delta=lastHealth-now;Enemy a=null;for(Enemy e:state.getAllEnemies())if(e.getAttackTarget()==workshop){a=e;break;}if(a!=null){workshop.markImpactFromWorldTile(a.getPosition(),delta);damageSector(workshop.toSectorRow(a.getPosition().getY()),workshop.toSectorColumn(a.getPosition().getX()),Math.max(8,delta));}}lastHealth=now;}
        private void damageSector(int row,int col,int n){for(InteriorMachine m:getMachines())if(m.getX()/3==col&&m.getY()/3==row)m.damage(Math.max(5,n/2));for(InteriorConveyor c:workshop.getInterior().getConveyors())if(c.getX()/3==col&&c.getY()/3==row)c.damage(Math.max(5,n/2));}
        private void destroyAll(){for(InteriorMachine m:getMachines())m.damage(100);for(InteriorConveyor c:workshop.getInterior().getConveyors())c.damage(100);}
        private void repair(){if(++repairTick<50)return;repairTick=0;Inventory s=state.getMainBuilding().getInventory();for(InteriorMachine m:getMachines())if(m.getDamage()>0&&s.remove(ResourceType.METAL,1)){m.repair(20);return;}for(InteriorConveyor c:workshop.getInterior().getConveyors())if(c.getDamage()>0&&s.remove(ResourceType.METAL,1)){c.repair(25);return;}}
    }

    public static final class VasyaRover extends Unit {
        private int tier=1; VasyaRover(Tile p,GameMap m){super(p,UnitType.ENGINEER,new Color(70,170,210),m);health=120;speed=.14f;} public int getTier(){return tier;}
        public boolean upgrade(){if(tier>=5)return false;tier++;speed=.14f+(tier-1)*.035f;health=120+tier*30;return true;}
        @Override public void render(Graphics g,int s){super.render(g,s);if(getPosition()!=null){g.setColor(Color.WHITE);g.drawString("R"+tier,getPosition().getX()*s+3,getPosition().getY()*s+11);}}
    }

    public static final class CombatRobot extends Unit {
        private Tile rally; private int cooldown; CombatRobot(Tile p,GameMap m){super(p,UnitType.SOLIDER,new Color(95,205,120),m);health=80;speed=.11f;} public void setRally(Tile t){rally=t;}
        public void updateCombat(List<Enemy> enemies){super.update();if(cooldown>0)cooldown--;Enemy best=null;double d=Double.MAX_VALUE;for(Enemy e:enemies){if(!e.isAlive())continue;double dx=e.getRealX()-getPosition().getX(),dy=e.getRealY()-getPosition().getY(),q=dx*dx+dy*dy;if(q<d){d=q;best=e;}}if(best!=null&&d<=3.0&&cooldown==0){best.takeDamage(6);cooldown=24;}else if(!isMoving()&&rally!=null)setTarget(rally);}
    }

    public final class ConstructionRover extends Unit {
        private Object job; private int repair;
        ConstructionRover(Tile p){super(p,UnitType.ENGINEER,new Color(225,155,70),map);health=180;speed=.10f;}
        @Override public void update(){super.update();if(isMoving())return;if(job==null)job=claimJob();if(job instanceof ConstructionSite)workSite((ConstructionSite)job);else if(job instanceof FacilityBlueprint)workFacility((FacilityBlueprint)job);else repairShell();}
        private void workSite(ConstructionSite s){if(!state.isConstructionPending(s)){release(s);return;}if(!s.hasAllMaterials()){if(getInventory().isEmpty()){if(!near(state.getMainBuilding().getPosition())){goNear(state.getMainBuilding().getPosition());return;}state.loadReservedMaterials(s,getInventory());}if(!near(s.getPosition())){goNear(s.getPosition());return;}s.deliverFrom(getInventory());return;}if(!near(s.getPosition())){goNear(s.getPosition());return;}constructionPhases.put(s,BlueprintPhase.BUILDING);s.work(1);if(s.isComplete()){state.completeConstruction(s,null);constructionPhases.put(s,BlueprintPhase.DONE);release(s);}}
        private void workFacility(FacilityBlueprint b){if(!b.isAlive()){release(b);return;}if(!b.hasAllMaterials()){if(getInventory().isEmpty()){if(!near(state.getMainBuilding().getPosition())){goNear(state.getMainBuilding().getPosition());return;}loadFacility(b);}if(!near(b.getPosition())){goNear(b.getPosition());return;}b.deliverFrom(getInventory());return;}if(!near(b.getPosition())){goNear(b.getPosition());return;}facilityPhases.put(b,BlueprintPhase.BUILDING);b.work(1);if(b.isComplete()){completeFacility(b);facilityPhases.put(b,BlueprintPhase.DONE);release(b);}}
        private void loadFacility(FacilityBlueprint b){Inventory s=state.getMainBuilding().getInventory();for(ResourceType r:ResourceType.values())s.transferTo(getInventory(),r,Math.min(b.getRemaining(r),getInventory().getFreeSpace()));}
        private void release(Object o){claimed.remove(o);job=null;}
        private boolean near(Tile t){Tile p=getPosition();if(p==null||t==null)return false;Building b=t.getBuilding();if(b!=null&&b.getPosition()!=null){for(int y=b.getPosition().getY();y<b.getPosition().getY()+b.getFootprintHeight();y++)for(int x=b.getPosition().getX();x<b.getPosition().getX()+b.getFootprintWidth();x++)if(Math.abs(p.getX()-x)+Math.abs(p.getY()-y)==1)return true;}return Math.abs(p.getX()-t.getX())+Math.abs(p.getY()-t.getY())<=1;}
        private void goNear(Tile t){Tile a=state.findBestAdjacentTile(getPosition(),t,this);if(a!=null)setTarget(a);}
        private void repairShell(){if(++repair<60)return;repair=0;for(FactoryState f:factories.values())if(f.workshop.isRuined()||f.workshop.getHealth()<f.workshop.getMaxHealth()){if(state.getMainBuilding().getInventory().remove(ResourceType.METAL,1))f.workshop.repairShell(15);return;}}
    }

    private final GameState state; private final GameMap map; private final Map<ConstructionSite,BlueprintPhase> constructionPhases=new LinkedHashMap<>();
    private final Map<FacilityBlueprint,BlueprintPhase> facilityPhases=new LinkedHashMap<>(); private final Map<Workshop,FactoryState> factories=new LinkedHashMap<>();
    private final Set<Object> claimed=new HashSet<>(); private final List<ConstructionRover> constructionRovers=new ArrayList<>(); private final List<CombatRobot> combatRobots=new ArrayList<>();
    private final Set<Long> wires=new LinkedHashSet<>(),pipes=new LinkedHashSet<>(); private boolean[][] explored; private final List<Alert>alerts=new ArrayList<>();
    private Worker vasya; private VasyaRover vasyaRover; private boolean boarded; private Workshop inside,targetWorkshop; private Tile cameraFocus,rallyPoint; private int tick,powerGeneration,powerDemand,powerDeficit;

    public RoadmapRuntime(GameState s,GameMap m){state=s;map=m;explored=new boolean[m.getHeight()][m.getWidth()];}
    public void bootstrap(){if(!state.getAllUnits().isEmpty()&&state.getAllUnits().get(0) instanceof Worker)vasya=(Worker)state.getAllUnits().get(0);Tile p=freeNear(state.getMainBuilding().getPosition(),2);if(p!=null){vasyaRover=new VasyaRover(p,map);state.addUnit(vasyaRover,p);}addConstructionRover();cameraFocus=state.getMainBuilding().getPosition();reveal(physicalVasya(),5);discoverFactories();}
    public void resetAfterStateRestart(){constructionPhases.clear();facilityPhases.clear();factories.clear();claimed.clear();constructionRovers.clear();combatRobots.clear();wires.clear();pipes.clear();alerts.clear();explored=new boolean[map.getHeight()][map.getWidth()];inside=targetWorkshop=null;boarded=false;bootstrap();}
    public void update(){tick++;discoverFactories();discoverPlans();updateVasya();for(ConstructionRover r:new ArrayList<>(constructionRovers))if(r.isAlive())r.update();for(FactoryState f:new ArrayList<>(factories.values()))f.update();updatePower();updateFluids();reveal(physicalVasya(),boarded?6:4);for(CombatRobot r:new ArrayList<>(combatRobots)){if(r.isAlive()){r.setRally(rallyPoint);r.updateCombat(state.getAllEnemies());}}if(tick%30==0)updateAlerts();}

    private void discoverFactories(){for(Building b:state.getAllBuildings())if(b instanceof Workshop&&!factories.containsKey(b))factories.put((Workshop)b,new FactoryState((Workshop)b));factories.keySet().removeIf(w->!state.getAllBuildings().contains(w));}
    private void discoverPlans(){for(ConstructionSite s:state.getConstructionQueue())constructionPhases.putIfAbsent(s,BlueprintPhase.PLANNED);constructionPhases.keySet().removeIf(s->!state.isConstructionPending(s)&&constructionPhases.get(s)!=BlueprintPhase.DONE);}
    private void updateVasya(){
        if(vasya==null)return;if(targetWorkshop!=null&&inside==null){if(!vasya.isAlive()&&boarded){if(near(vasyaRover.getPosition(),targetWorkshop.getPosition()))disembark();else vasyaRover.setTarget(freeNear(targetWorkshop.getPosition(),2));return;}if(near(vasya.getPosition(),targetWorkshop.getPosition()))enter(targetWorkshop);else moveVasyaToward(targetWorkshop.getPosition());return;}
        if(inside!=null){FactoryState f=factories.get(inside);if(f!=null)f.markOne();return;}
        ConstructionSite plan=null;for(ConstructionSite s:state.getConstructionQueue())if(constructionPhases.get(s)==BlueprintPhase.PLANNED||constructionPhases.get(s)==BlueprintPhase.MARKING){plan=s;break;}
        if(plan!=null){if(near(physicalVasya(),plan.getPosition())){constructionPhases.put(plan,BlueprintPhase.MARKING);Integer n=markTicks.get(plan);n=n==null?1:n+1;markTicks.put(plan,n);if(n>=30)constructionPhases.put(plan,BlueprintPhase.WAITING_FOR_BUILDER);}else moveVasyaToward(plan.getPosition());return;}
        FacilityBlueprint fp=null;for(Map.Entry<FacilityBlueprint,BlueprintPhase>e:facilityPhases.entrySet())if(e.getValue()==BlueprintPhase.PLANNED||e.getValue()==BlueprintPhase.MARKING){fp=e.getKey();break;}
        if(fp!=null){if(near(physicalVasya(),fp.getPosition())){facilityPhases.put(fp,BlueprintPhase.MARKING);Integer n=facilityMarkTicks.get(fp);n=n==null?1:n+1;facilityMarkTicks.put(fp,n);if(n>=30)facilityPhases.put(fp,BlueprintPhase.WAITING_FOR_BUILDER);}else moveVasyaToward(fp.getPosition());return;}
        if(cameraFocus!=null&&distance(physicalVasya(),cameraFocus)>3)moveVasyaToward(cameraFocus);
    }
    private final Map<ConstructionSite,Integer>markTicks=new HashMap<>(); private final Map<FacilityBlueprint,Integer>facilityMarkTicks=new HashMap<>();
    private void moveVasyaToward(Tile t){if(t==null||vasya==null)return;if(distance(physicalVasya(),t)>8&&vasyaRover!=null&&vasyaRover.isAlive()){if(!boarded){if(near(vasya.getPosition(),vasyaRover.getPosition()))board();else vasya.setTarget(vasyaRover.getPosition());}else if(!vasyaRover.isMoving())vasyaRover.setTarget(t);return;}if(boarded){disembark();return;}if(vasya.isAlive()&&!vasya.isMoving())vasya.setTarget(t);}
    private void board(){if(vasya.getPosition()!=null&&vasya.getPosition().getUnit()==vasya)vasya.getPosition().setUnit(null);vasya.setAlive(false);boarded=true;}
    private void disembark(){Tile p=freeNear(vasyaRover==null?state.getMainBuilding().getPosition():vasyaRover.getPosition(),2);if(p!=null){vasya.setAlive(true);vasya.move(p);boarded=false;}}
    private void enter(Workshop w){if(vasya.getPosition()!=null&&vasya.getPosition().getUnit()==vasya)vasya.getPosition().setUnit(null);vasya.setAlive(false);inside=w;targetWorkshop=null;}
    private void exit(){Workshop w=inside;inside=null;if(w!=null){Tile p=freeNear(w.getPosition(),2);if(p!=null){vasya.setAlive(true);vasya.move(p);}}}

    private Object claimJob(){for(Map.Entry<ConstructionSite,BlueprintPhase>e:constructionPhases.entrySet())if((e.getValue()==BlueprintPhase.WAITING_FOR_BUILDER||e.getValue()==BlueprintPhase.WAITING_FOR_RESOURCES||e.getValue()==BlueprintPhase.BUILDING)&&claimed.add(e.getKey())){constructionPhases.put(e.getKey(),BlueprintPhase.WAITING_FOR_RESOURCES);return e.getKey();}for(Map.Entry<FacilityBlueprint,BlueprintPhase>e:facilityPhases.entrySet())if((e.getValue()==BlueprintPhase.WAITING_FOR_BUILDER||e.getValue()==BlueprintPhase.WAITING_FOR_RESOURCES||e.getValue()==BlueprintPhase.BUILDING)&&claimed.add(e.getKey())){facilityPhases.put(e.getKey(),BlueprintPhase.WAITING_FOR_RESOURCES);return e.getKey();}return null;}
    private boolean addConstructionRover(){Tile p=freeNear(state.getMainBuilding().getPosition(),3);if(p==null)return false;ConstructionRover r=new ConstructionRover(p);state.addUnit(r,p);constructionRovers.add(r);return true;}
    private void completeFacility(FacilityBlueprint b){Tile p=b.getPosition();FacilityKind k=b.getKind();state.removeBuilding(b);FacilityBuilding f=new FacilityBuilding(p,k);state.addBuilding(f,p);factories.put(f,new FactoryState(f));}

    private void updatePower(){powerGeneration=powerDemand=0;for(FactoryState f:factories.values()){f.setPowerAvailable(false);powerGeneration+=f.getGeneration();powerDemand+=f.getPowerDemand();}for(Set<Long>c:components(wires)){int g=0,d=0;List<FactoryState>fs=new ArrayList<>();for(FactoryState f:factories.values())if(touches(f.workshop,c)){fs.add(f);g+=f.getGeneration();d+=f.getPowerDemand();}boolean ok=g>0&&g>=d;for(FactoryState f:fs)f.setPowerAvailable(ok);}powerDeficit=Math.max(0,powerDemand-powerGeneration);}
    private void updateFluids(){if(pipes.isEmpty())return;for(Set<Long>c:components(pipes)){List<FactoryState>fs=new ArrayList<>();List<CombatTower>ts=new ArrayList<>();boolean water=false;List<Tile>oil=new ArrayList<>();for(long q:c){Tile t=map.getTile(kx(q),ky(q));if(t==null)continue;if(t.getType()==TileType.WATER)water=true;if(t.hasResource()&&t.getResource().getType()==ResourceType.OIL)oil.add(t);}for(FactoryState f:factories.values())if(touches(f.workshop,c))fs.add(f);for(Building b:state.getAllBuildings())if(b instanceof CombatTower&&touches(b,c))ts.add((CombatTower)b);if(tick%20==0)for(FactoryState f:fs)if(f.workshop instanceof FacilityBuilding&&((FacilityBuilding)f.workshop).kind==FacilityKind.CHEMICAL_PLANT){if(water)f.addFluid(ResourceType.WATER,2);for(Tile t:oil)if(t.hasResource()){int n=t.getResource().extract(1);f.addFluid(ResourceType.OIL,n);if(t.getResource().isDepleted())t.setResource(null);break;}}for(CombatTower t:ts)if(t.getCoolant()<12)for(FactoryState f:fs)if(f.removeFluid(ResourceType.COOLANT,1)>0){t.addCoolant(1);break;}}}
    private void updateAlerts(){alerts.clear();if(state.getWaveManager()!=null&&state.getWaveManager().isCountingDownToWave()){int s=state.getWaveManager().getSecondsUntilNextWave();if(s<=45)alerts.add(new Alert("⚠ WAVE IN "+s+" SEC",state.getEnemySpawnPoint().getPosition()));}for(FactoryState f:factories.values())if(f.workshop.isRuined())alerts.add(new Alert("⚠ FACTORY RUINED",f.workshop.getPosition()));else if(f.workshop.getHealth()<f.workshop.getMaxHealth()*.65)alerts.add(new Alert("⚠ FACTORY TAKING DAMAGE",f.workshop.getPosition()));if(powerDeficit>0)alerts.add(new Alert("⚠ BASE POWER DEFICIT: "+powerDeficit,state.getMainBuilding().getPosition()));if(state.getMainBuilding().getHealth()<House.MAX_HEALTH/2)alerts.add(new Alert("⚠ MAIN BASE UNDER ATTACK",state.getMainBuilding().getPosition()));for(Building b:state.getAllBuildings())if(b.getType()==BuildingType.WALL&&b.getHealth()<45){alerts.add(new Alert("⚠ PERIMETER WALL UNDER ATTACK",b.getPosition()));break;}}

    public Worker getVasya(){return vasya;} public VasyaRover getVasyaRover(){return vasyaRover;} public Workshop getVasyaInsideWorkshop(){return inside;}
    public List<ConstructionRover> getConstructionRovers(){return Collections.unmodifiableList(constructionRovers);} public List<CombatRobot> getCombatRobots(){return Collections.unmodifiableList(combatRobots);}
    public List<Alert> getAlerts(){return Collections.unmodifiableList(alerts);} public Tile getRallyPoint(){return rallyPoint;} public int getPowerGeneration(){return powerGeneration;} public int getPowerDemand(){return powerDemand;} public int getPowerDeficit(){return powerDeficit;}
    public BlueprintPhase getPhase(ConstructionSite s){return constructionPhases.getOrDefault(s,BlueprintPhase.DONE);} public BlueprintPhase getPhase(FacilityBlueprint b){return facilityPhases.getOrDefault(b,BlueprintPhase.DONE);}
    public FactoryState getFactoryState(Workshop w){return factories.get(w);} public Map<Workshop,FactoryState> getFactoryStates(){return Collections.unmodifiableMap(factories);}
    public void setCameraFocus(Tile t){cameraFocus=t;} public void requestEnterWorkshop(Workshop w){targetWorkshop=w;} public void requestExitWorkshop(Workshop w){if(inside==w)exit();else if(targetWorkshop==w)targetWorkshop=null;}
    public boolean queueInteriorConveyor(Workshop w,int x,int y,Direction d){FactoryState f=factories.get(w);return f!=null&&f.queueConveyor(x,y,d);} public boolean queueInteriorMachine(Workshop w,int x,int y,MachineType t,Direction d){FactoryState f=factories.get(w);return f!=null&&f.queueMachine(x,y,t,d);}
    public ResourceType cycleGatewayFilter(Workshop w,int x,int y){FactoryPort p=w==null?null:w.findPortForGatewayCell(x,y);if(p==null||!p.isInput())return null;return p.cycleLaneFilter(w.getGatewayLaneIndex(p,x,y));}
    public boolean placeFacilityPlan(FacilityKind k,Tile p){if(k==null||p==null||!freeFootprint(p,3,3)||!has(k.getCost()))return false;FacilityBlueprint b=new FacilityBlueprint(p,k);if(!state.addBuilding(b,p))return false;facilityPhases.put(b,BlueprintPhase.PLANNED);return true;}
    public boolean toggleWire(int x,int y){if(map.getTile(x,y)==null)return false;long q=key(x,y);if(!wires.remove(q))wires.add(q);return true;} public boolean togglePipe(int x,int y){if(map.getTile(x,y)==null)return false;long q=key(x,y);if(!pipes.remove(q))pipes.add(q);return true;} public boolean hasWire(int x,int y){return wires.contains(key(x,y));} public boolean hasPipe(int x,int y){return pipes.contains(key(x,y));}
    public boolean isExplored(int x,int y){return x>=0&&y>=0&&x<map.getWidth()&&y<map.getHeight()&&explored[y][x];} public int getExploredPercent(){int n=0;for(boolean[]r:explored)for(boolean b:r)if(b)n++;return 100*n/Math.max(1,map.getWidth()*map.getHeight());}
    public void setRallyPoint(Tile t){rallyPoint=t;} public boolean deployCombatRobot(){if(!state.getMainBuilding().getInventory().remove(ResourceType.ROBOT_KIT,1))return false;Tile p=freeNear(state.getMainBuilding().getPosition(),3);if(p==null){state.getMainBuilding().getInventory().add(ResourceType.ROBOT_KIT,1);return false;}CombatRobot r=new CombatRobot(p,map);state.addUnit(r,p);combatRobots.add(r);return true;}
    public boolean buildConstructionRover(){if(!state.getMainBuilding().getInventory().remove(ResourceType.ROBOT_KIT,1))return false;if(addConstructionRover())return true;state.getMainBuilding().getInventory().add(ResourceType.ROBOT_KIT,1);return false;}
    public boolean addInternalBot(Workshop w){FactoryState f=factories.get(w);if(f==null||!state.getMainBuilding().getInventory().remove(ResourceType.ROBOT_KIT,1))return false;f.addInternalBot();return true;}
    public boolean upgradeVasyaRover(){if(vasyaRover==null||vasyaRover.getTier()>=5||!state.getMainBuilding().getInventory().remove(ResourceType.COMPONENT,2))return false;if(vasyaRover.upgrade())return true;state.getMainBuilding().getInventory().add(ResourceType.COMPONENT,2);return false;}
    public boolean isVasyaTile(Tile t){return t!=null&&physicalVasya()==t;}

    private boolean has(Map<ResourceType,Integer>c){Inventory i=state.getMainBuilding().getInventory();for(Map.Entry<ResourceType,Integer>e:c.entrySet())if(!i.has(e.getKey(),e.getValue()))return false;return true;}
    private boolean freeFootprint(Tile p,int w,int h){for(int y=0;y<h;y++)for(int x=0;x<w;x++){Tile t=map.getTile(p.getX()+x,p.getY()+y);if(t==null||!t.isPassable()||t.hasBuilding()||t.hasUnit()||t.hasResource())return false;}return true;}
    private Tile physicalVasya(){if(inside!=null)return inside.getPosition();if(boarded&&vasyaRover!=null)return vasyaRover.getPosition();return vasya==null?null:vasya.getPosition();}
    private double distance(Tile a,Tile b){return a==null||b==null?0:Math.hypot(a.getX()-b.getX(),a.getY()-b.getY());} private boolean near(Tile a,Tile b){return distance(a,b)<=2.1;}
    private Tile freeNear(Tile o,int radius){if(o==null)return null;for(int r=1;r<=radius;r++)for(int y=o.getY()-r;y<=o.getY()+r;y++)for(int x=o.getX()-r;x<=o.getX()+r;x++){Tile t=map.getTile(x,y);if(t!=null&&t.isPassable()&&!t.hasBuilding()&&!t.hasUnit())return t;}return null;}
    private void reveal(Tile t,int r){if(t==null)return;for(int y=t.getY()-r;y<=t.getY()+r;y++)for(int x=t.getX()-r;x<=t.getX()+r;x++)if(x>=0&&y>=0&&x<map.getWidth()&&y<map.getHeight()&&(x-t.getX())*(x-t.getX())+(y-t.getY())*(y-t.getY())<=r*r)explored[y][x]=true;}
    private long key(int x,int y){return((long)x<<32)^(y&0xffffffffL);} private int kx(long q){return(int)(q>>32);} private int ky(long q){return(int)q;}
    private List<Set<Long>> components(Set<Long>s){List<Set<Long>>out=new ArrayList<>();Set<Long>u=new HashSet<>(s);while(!u.isEmpty()){long a=u.iterator().next();Set<Long>c=new HashSet<>();Deque<Long>q=new ArrayDeque<>();q.add(a);u.remove(a);while(!q.isEmpty()){long z=q.remove();c.add(z);int x=kx(z),y=ky(z);long[]ns={key(x+1,y),key(x-1,y),key(x,y+1),key(x,y-1)};for(long n:ns)if(u.remove(n))q.add(n);}out.add(c);}return out;}
    private boolean touches(Building b,Set<Long>c){if(b==null||b.getPosition()==null)return false;int x0=b.getPosition().getX(),y0=b.getPosition().getY();for(int y=y0-1;y<=y0+b.getFootprintHeight();y++)for(int x=x0-1;x<=x0+b.getFootprintWidth();x++)if(c.contains(key(x,y)))return true;return false;}
}
