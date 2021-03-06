import sim.engine.*;
import sim.util.Double2D;

/**
 * An "agent" that creates food and adds it to the environment. The FoodMaker
 * tries to achieve conservation of energy.
 */
public class FoodMaker implements Steppable
{
    // Food-maker parameters:
    protected static final double foodEnergyPerAgent = Agent.initialEnergy * 1.5;
    protected static final double energyDeficitThreshold = 500;
    protected static final int spawnCount = 15;
    protected static final double clusterVariance = 1;

    /**
     * If the totalEnergyFood of the simulation is smaller than numAgentsInitial
     * * foodEnergyPerAgent by more than energyDeficitThreshold, creates
     * spawnCount food items and adds them to the environment and the schedule.
     */
    public void step(final SimState state)
    {
        DiseaseSpread sim = (DiseaseSpread)state;

        double foodEnergyTarget = sim.numAgentsInitial * foodEnergyPerAgent;
        if(foodEnergyTarget - sim.getTotalEnergyFood() > energyDeficitThreshold) {
            // Spawn new food items in a Gaussian cluster.
            double cx = sim.random.nextDouble() * sim.xMax,
                   cy = sim.random.nextDouble() * sim.yMax;
            int addedFood = 0;
            while(addedFood < spawnCount) {
                double dx = sim.random.nextGaussian() * clusterVariance,
                       dy = sim.random.nextGaussian() * clusterVariance;
                double x = DiseaseSpread.clamp(cx + dx, 0, DiseaseSpread.xMax),
                       y = DiseaseSpread.clamp(cy + dy, 0, DiseaseSpread.yMax);
                Double2D loc = new Double2D(x, y);
                Food item = new Food();
                sim.environment.setObjectLocation(item, loc);
                item.scheduleItem = sim.schedule.scheduleRepeating(item, Food.stepInterval);
                addedFood++;
                sim.totalEnergy += item.energy;
            }
            System.out.println("FoodMaker spawned food");
        }
    }
}
