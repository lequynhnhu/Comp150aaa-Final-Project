import sim.portrayal.continuous.*;
import sim.engine.*;
import sim.display.*;
import javax.swing.*;
import java.awt.Color;


public class DiseaseSpreadWithUI extends GUIState
{

    public Display2D display;
    public JFrame displayFrame;

    ContinuousPortrayal2D vidPortrayal = new ContinuousPortrayal2D();

    public static void main(String[] args)
    {
        new DiseaseSpreadWithUI().createController();
    }

    public DiseaseSpreadWithUI() { super(new DiseaseSpread( System.currentTimeMillis())); }
    public DiseaseSpreadWithUI(SimState state) { super(state); }

    public static String getName() { return "DiseaseSpread"; }
        
    public void start()
    {
        super.start();
        setupPortrayals();
    }

    public void load(SimState state)
    {
        super.load(state);
        setupPortrayals();
    }
        
    public void setupPortrayals()
    {
        // tell the portrayals what to portray and how to portray them
        vidPortrayal.setField(((DiseaseSpread)state).environment);
            
        // reschedule the displayer
        display.reset();
        display.setBackdrop(Color.white);
                
        // redraw the display
        display.repaint();
    }

    public void init(Controller c)
    {
        super.init(c);

        // make the displayer
        display = new Display2D(800,600,this);

        displayFrame = display.createFrame();
        displayFrame.setTitle("Disease Spread Demonstration Display");
        c.registerFrame(displayFrame);   // register the frame so it appears in the "Display" list
        displayFrame.setVisible(true);
        display.attach( vidPortrayal, "Agents" );
    }
        
    public void quit()
    {
        super.quit();
        
        if (displayFrame!=null) displayFrame.dispose();
        displayFrame = null;
        display = null;
    }

}
