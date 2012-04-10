import sim.engine.*;
import sim.util.*;
import java.util.ArrayList;

/**
 * An agent in the simulation. The agent has a certain amount of energy. The
 * agent can be healthy or infected.
 */
public class Agent implements Steppable
{
    // Agent parameters:
    protected static final double initialEnergy = 1000;
    protected static final double satiatedEnergy = 1200;
    protected static final double energyDrainPerStep = 10;
    protected static final double sensoryRange = 20;
    protected static final double infectionRange = 10;
    protected static final double eatingRange = 5;

    protected static final double foodFactor = 1.0;
    protected static final double defaultFlockingFactor = 0.7;
    protected static double flockingFactor;
    protected static final double repulsionFactor = 0.8;
    protected static final double randomnessFactor = 0.1;
    protected static final double orientationFactor = 1.1;
        
    protected static final double minDistance = 0.5; 

    // Agent data:
    public int id;
    public Double2D location;
    public double energy;
    public boolean infected;
    protected Stoppable scheduleItem;
    
    public Double2D orientation;

    // agent behavior
    protected static boolean avoidSick = true;
    protected static boolean flocking = true;
    protected static boolean avoidEveryone = false;

    /** Initializes an agent with the given id and location. */
    public Agent(int id, Double2D location, boolean infected)
    {
        this.id = id;
        this.location = location;
        this.energy = initialEnergy;
        this.infected = infected;
        this.orientation = new Double2D();
    }

    /** Returns true if the agent is satiated (cannot eat right now). */
    public boolean isSatiated()
    {
        return energy > satiatedEnergy;
    }

    /** Updates the agent at every step of the simulation. */
    public void step(final SimState state)
    {
        DiseaseSpread sim = (DiseaseSpread)state;

        // Drain energy and remove agent from environment & schedule if the
        // energy drops to zero.
        double drain = energyDrainPerStep;
        if(infected) {
            drain *= sim.disease.energyDrainMultiplier;
        }
        energy -= drain;
        if(energy <= 0) {
            sim.environment.remove(this);
            scheduleItem.stop();
            System.out.println("Agent " + id + " died");
            return;
        }

        // Collect nearby items and sort them by type.
        Bag neighbors = sim.environment.getObjectsWithinDistance(location, sensoryRange);
        ArrayList<Agent> nearbyAgents = new ArrayList<Agent>();
        ArrayList<Food> nearbyFood = new ArrayList<Food>();
        for(int i = 0; i < neighbors.numObjs; i++) {
            // Ignore ourselves.
            if(neighbors.objs[i] == this) {
                continue;
            }
            // Doc says getObjectsWithinDistance() may return objects outside the
            // range, so we have to ignore those objects.
            Double2D objLoc = sim.environment.getObjectLocation(neighbors.objs[i]); 
            if(objLoc.distance(this.location) > sensoryRange) {
                continue;
            }
            if(neighbors.objs[i] instanceof Food) {
                nearbyFood.add((Food)neighbors.objs[i]);
            } else if(neighbors.objs[i] instanceof Agent) {
                nearbyAgents.add((Agent)neighbors.objs[i]);
            }
        }
       
        /* 
        System.out.println("Agent " + id + " sees " + nearbyFood.size() + " food");
        String s = new String("Agent " + id + " sees ");
        if (nearbyAgents.size() == 0) {
            s += "no agents";
        }
        for (Agent other : nearbyAgents) {
            s += " " + other.id + ",";
        }
        System.out.println(s); 
        */

        // Take appropriate actions. These are factored out into different
        // functions for readability.
        stepEat(state, nearbyFood);
        stepUpdateInfected(state, nearbyAgents);
        stepMove(state, nearbyFood, nearbyAgents);
    }

    /**
     * Go through the list of nearby food and eat the best item we can find.
     * The eaten food item gets removed from the environment and the schedule.
     */
    private void stepEat(final SimState state, ArrayList<Food> nearbyFood)
    {
        DiseaseSpread sim = (DiseaseSpread)state;

        // We can't eat if we are satiated.
        if(isSatiated()) {
            return;
        }

        // Find the item with the most energy that is close enough to eat.
        Food bestItem = null;
        for(Food item: nearbyFood) {
            Double2D itemLoc = sim.environment.getObjectLocation(item);
            if(itemLoc.distance(this.location) > eatingRange) {
                continue;
            }
            if(bestItem == null || item.energy > bestItem.energy) {
                bestItem = item;
            }
        }

        // Eat the best found item, if any.
        if(bestItem != null) {
            energy += bestItem.energy;
            bestItem.energy = 0;
            sim.environment.remove(bestItem);
            // bestItem will be removed from schedule on its next step().
            System.out.println("Agent " + id + " ate");
        }
    }

    /**
     * If infected, recovers from disease with some probability. If healthy,
     * gets infected with some probability if there are nearby infected agents.
     */
    private void stepUpdateInfected(final SimState state,
                                    ArrayList<Agent> nearbyAgents)
    {
        DiseaseSpread sim = (DiseaseSpread)state;

        if(infected) {
            // Recover with some probability.
            if(sim.random.nextDouble() <= sim.disease.probRecovery) {
                infected = false;
                System.out.println("Agent " + id + " recovered");
            }
        } else {
            // Figure out if there is an infected agent nearby.
            boolean foundInfected = false;
            for(Agent agent: nearbyAgents) {
                if(agent.infected &&
                   agent.location.distance(this.location) <= infectionRange) {
                    foundInfected = true;
                    break;
                }
            }
            // If there is an infected agent nearby, get infected with some probability.
            if(foundInfected && sim.random.nextDouble() <= sim.disease.probTransmission) {
                infected = true;
                System.out.println("Agent " + id + " got infected");
            }
        }
    }

    /**
     * Moves in an appropriate direction based on what food items and other
     * agents are nearby. This takes into account infection and flocking
     * parameters.
     */
    private void stepMove(final SimState state,
                          ArrayList<Food> nearbyFood,
                          ArrayList<Agent> nearbyAgents)
    {
        DiseaseSpread sim = (DiseaseSpread)state;

        MutableDouble2D avgOrientation = new MutableDouble2D(this.orientation),
                        foodAttraction = new MutableDouble2D(),
                        agentAttraction = new MutableDouble2D(),
                        agentRepulsion = new MutableDouble2D();

        // primary factor is the orientation of our neighbors if flocking
        if (flocking) {
            MutableDouble2D sumOrientation = new MutableDouble2D();
            for (Agent other : nearbyAgents) {
                double d = other.location.distance(this.location); 
                if (d < minDistance) {
                    d = minDistance;
                }
                Double2D force = other.orientation.multiply(1.0 / (d * d));
                sumOrientation.addIn(force);
            }

            avgOrientation.addIn(sumOrientation.multiplyIn(0.7));
        }
      

        // agent attraction / repulsion
        for (Agent other : nearbyAgents) {
            Double2D force;
            double d = other.location.distance(this.location);
            if (d < minDistance) {
                d = minDistance;
            }

            if (avoidEveryone) {
                force = this.location.subtract(other.location).multiply(1.0 / (d * d));    
                agentRepulsion.addIn(force);
            }
            else if (other.infected) {
                if (avoidSick) {
                    force = this.location.subtract(other.location).multiply(1.0 / (d * d));
                    agentRepulsion.addIn(force);    
                } else if (flocking) {
                    force = other.location.subtract(this.location).multiply(1.0 / (d * d));
                    agentAttraction.addIn(force);
                }
            } else if (flocking) {
                force = other.location.subtract(this.location).multiply(1.0 / (d * d));
                agentAttraction.addIn(force);
            }
        }


        // We are attracted to food.
        for (Food item : nearbyFood) {
            Double2D itemLoc = sim.environment.getObjectLocation(item);
            if(itemLoc == null) {  // this may be null if we just ate the item
                continue;
            }
            double d = itemLoc.distance(this.location);
            if (d < minDistance) {
                d = minDistance;
            }
            Double2D force = itemLoc.subtract(this.location).multiply(item.energy / (d * d));
            foodAttraction.addIn(force);
        }

        MutableDouble2D randomDirection = new MutableDouble2D(sim.random.nextDouble(), sim.random.nextDouble());
        randomDirection.normalize();
        
        if (avgOrientation.length() > 0) {
            avgOrientation.normalize();
        }
        if (foodAttraction.length() > 0) {
            foodAttraction.normalize();
        }
        if (agentAttraction.length() > 0) {
            agentAttraction.normalize();
        }
        if (agentRepulsion.length() > 0) {
            agentRepulsion.normalize();
        }

        MutableDouble2D sumForces = new MutableDouble2D();
        
        sumForces.addIn(
                avgOrientation.multiplyIn(orientationFactor)).addIn(
                foodAttraction.multiplyIn(foodFactor)).addIn(
                agentAttraction.multiplyIn(flockingFactor)).addIn(
                agentRepulsion.multiplyIn(repulsionFactor)).addIn(
                randomDirection.multiplyIn(randomnessFactor));
                

        // Move to the location given by the sum of forces.
        // Careful, need to setObjectLocation and *also* update this.location.
        if(sumForces.length() > 0) {
            sumForces.normalize();
        }

        this.orientation = new Double2D(sumForces);

        sumForces.addIn(this.location);
        //System.out.println("current location: " + this.location + " new location: " + sumForces);
        this.location = new Double2D(sumForces);
        sim.environment.setObjectLocation(this, this.location);
    }
}
