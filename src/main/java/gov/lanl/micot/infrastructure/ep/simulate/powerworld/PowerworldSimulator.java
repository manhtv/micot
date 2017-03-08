package gov.lanl.micot.infrastructure.ep.simulate.powerworld;

import java.io.IOException;
import java.util.ArrayList;

import gov.lanl.micot.infrastructure.ep.io.powerworld.PowerworldIOConstants;
import gov.lanl.micot.infrastructure.ep.io.powerworld.PowerworldModelFile;
import gov.lanl.micot.infrastructure.ep.model.Bus;
import gov.lanl.micot.infrastructure.ep.model.ElectricPowerFlowConnection;
import gov.lanl.micot.infrastructure.ep.model.ElectricPowerModel;
import gov.lanl.micot.infrastructure.ep.model.ElectricPowerNode;
import gov.lanl.micot.infrastructure.ep.model.Generator;
import gov.lanl.micot.infrastructure.ep.model.powerworld.PowerworldModel;
import gov.lanl.micot.infrastructure.ep.model.powerworld.PowerworldModelFactory;
import gov.lanl.micot.infrastructure.ep.simulate.ElectricPowerSimulatorImpl;
import gov.lanl.micot.infrastructure.model.Component;
import gov.lanl.micot.infrastructure.model.FlowConnection;
import gov.lanl.micot.util.collection.Pair;
import gov.lanl.micot.util.collection.Triple;
import gov.lanl.micot.util.io.dcom.ComDataObject;
import gov.lanl.micot.util.io.dcom.ComObject;
import gov.lanl.micot.util.io.dcom.ComObjectFactory;
import gov.lanl.micot.util.io.dcom.ComObjectUtilities;

/**
 * Specific instantation of the Powerworld simulator
 * 
 * @author Russell Bent
 */
public class PowerworldSimulator extends ElectricPowerSimulatorImpl {

  private boolean debug = false;
  private String preOutputModelFile = "preTemp.pwb";
  private String postOutputModelFile = "postTemp.pwb";
  
  /**
   * Constructor
   * 
   * @param nextGenerationPFWFilename
   */
  protected PowerworldSimulator() {
    super();
  }

  @Override
  protected SimulatorSolveState simulateModel(ElectricPowerModel model) {
  	for (Bus bus : model.getBuses()) {
			ElectricPowerNode node = model.getNode(bus);
  		if (!bus.getActualStatus() || !bus.getDesiredStatus()) {
  			for (Component asset : node.getComponents(Component.class)) {
  				asset.setDesiredStatus(false);
  				asset.setActualStatus(false);  				
  			}
  			
  			for (FlowConnection connection : model.getFlowConnections(node)) {
  			  connection.setActualStatus(false);
          connection.setDesiredStatus(false);
  			}
  			
  		}  		
  	}

  	// make sure we are in a power world regime
  	PowerworldModelFactory factory = PowerworldModelFactory.getInstance();
  	PowerworldModel powerWorldModel = factory.constructPowerworldModel(model);
  	ComObject powerworld = powerWorldModel.getPowerworld();

  	
  	if (debug) {
      PowerworldModelFile mf = new PowerworldModelFile();
      try {
        mf.saveFile(preOutputModelFile, powerWorldModel);
      }
      catch (IOException e) {
        e.printStackTrace();
      }
    }
  	
  	
  	// go into run mode
  	String scriptcommand = PowerworldIOConstants.RUN_MODE;
  	ComDataObject object = powerworld.callData(PowerworldIOConstants.RUN_SCRIPT_COMMAND, scriptcommand);
  	ArrayList<ComDataObject> o = object.getArrayValue();
    String errorString = o.get(0).getStringValue();
    if (!errorString.equals("")) {
      System.err.println("Error getting into run mode: " + errorString);                
    }

  	// run the power flow solve
  	scriptcommand = PowerworldIOConstants.POWER_FLOW_COMMAND_RECT_NEWTON;
  	object = powerworld.callData(PowerworldIOConstants.RUN_SCRIPT_COMMAND, scriptcommand);
  	o = object.getArrayValue();
    errorString = o.get(0).getStringValue();
    SimulatorSolveState s = SimulatorSolveState.CONVERGED_SOLUTION;
    if (!errorString.equals("")) {
      System.err.println("Trying robust solver");

      // drop into robust solving if first solve fails, and do a flat restart
      scriptcommand = PowerworldIOConstants.RESET_TO_FLAT_START;
      object = powerworld.callData(PowerworldIOConstants.RUN_SCRIPT_COMMAND, scriptcommand);
      o = object.getArrayValue();
      errorString = o.get(0).getStringValue();
      if (!errorString.equals("")) {
        System.err.println("Error running flat start: " + errorString);
      }
            
      scriptcommand = PowerworldIOConstants.POWER_FLOW_COMMAND_RECT_NEWTON;
      object = powerworld.callData(PowerworldIOConstants.RUN_SCRIPT_COMMAND, scriptcommand);
      o = object.getArrayValue();
      errorString = o.get(0).getStringValue();
      if (!errorString.equals("")) {
        System.err.println("Error running power flow solver: " + errorString);
        s = SimulatorSolveState.ERROR_SOLUTION;
        
        if (debug) {
          PowerworldModelFile mf = new PowerworldModelFile();
          try {
            mf.saveFile(postOutputModelFile, powerWorldModel);
          }
          catch (IOException e) {
            e.printStackTrace();
          }
        }
      }
    }

    // get the flows and losses
    String fields[] = new String[]{
    		PowerworldIOConstants.BRANCH_BUS_FROM_NUM, 
    		PowerworldIOConstants.BRANCH_BUS_TO_NUM, 
    		PowerworldIOConstants.BRANCH_NUM, 
        PowerworldIOConstants.BRANCH_REACTIVE_LOSS, 
        PowerworldIOConstants.BRANCH_REAL_LOSS, 
        PowerworldIOConstants.BRANCH_FROM_REACTIVE_FLOW, 
        PowerworldIOConstants.BRANCH_TO_REACTIVE_FLOW, 
        PowerworldIOConstants.BRANCH_FROM_REAL_FLOW, 
        PowerworldIOConstants.BRANCH_TO_REAL_FLOW, 
        PowerworldIOConstants.BRANCH_STATUS};
    
    for (ElectricPowerFlowConnection line : model.getFlowConnections()) {
    	Triple<Integer,Integer,Integer> legacyid = powerWorldModel.getConnectionId(line);
      String values[] = new String[] {legacyid.getOne()+"", legacyid.getTwo()+"", legacyid.getThree()+"", "", "", "", "", "", "", ""};
          
      ComDataObject dataObject = powerworld.callData(PowerworldIOConstants.GET_PARAMETERS_SINGLE_ELEMENT, PowerworldIOConstants.BRANCH, fields, values);
      ArrayList<ComDataObject> branchData = dataObject.getArrayValue();
      errorString = branchData.get(0).getStringValue();
      if (!errorString.equals("")) {
        System.err.println("Error getting powerworld branch solution data: " + errorString);                
      }
      
      ArrayList<ComDataObject> bData = branchData.get(1).getArrayValue();                       
      String reactiveLossStr = bData.get(3).getStringValue();
      String realLossStr = bData.get(4).getStringValue();
      String reactiveFromStr = bData.get(5).getStringValue();
      String reactiveToStr = bData.get(6).getStringValue();    
      String realFromStr = bData.get(7).getStringValue();
      String realToStr = bData.get(8).getStringValue();
      String status = bData.get(9).getStringValue();
      
      double reactiveLoss = Double.parseDouble(reactiveLossStr);
      double realLoss = Double.parseDouble(realLossStr);
      double reactiveFrom = Double.parseDouble(reactiveFromStr);
      double reactiveTo = Double.parseDouble(reactiveToStr);
      double realFrom = Double.parseDouble(realFromStr);
      double realTo = Double.parseDouble(realToStr);
      double mwFlow = Math.max(realFrom, realTo);
      double mVarFlow = Math.max(reactiveFrom, reactiveTo);
      
      line.setMWFlow(mwFlow);
      line.setMVarFlow(mVarFlow);
      line.setRealLoss(realLoss);
      line.setReactiveLoss(reactiveLoss);
      line.setAttribute(ElectricPowerFlowConnection.MVAR_FLOW_SIDE1_KEY, reactiveFrom);
      line.setAttribute(ElectricPowerFlowConnection.MVAR_FLOW_SIDE2_KEY, reactiveTo);
      line.setAttribute(ElectricPowerFlowConnection.MW_FLOW_SIDE1_KEY, realFrom);
      line.setAttribute(ElectricPowerFlowConnection.MW_FLOW_SIDE2_KEY, realTo);
    }

    // get the bus data
    fields = new String[]{PowerworldIOConstants.BUS_NUM, 
    		PowerworldIOConstants.BUS_PU_VOLTAGE, 
    		PowerworldIOConstants.BUS_ANGLE}; 
    for (Bus bus : model.getBuses()) {
    	Integer id = powerWorldModel.getBusId(bus);
      String values[] = new String[] {id+"", "", ""};
      
      ComDataObject dataObject = powerworld.callData(PowerworldIOConstants.GET_PARAMETERS_SINGLE_ELEMENT, PowerworldIOConstants.BUS, fields, values);
      ArrayList<ComDataObject> busData = dataObject.getArrayValue();
      errorString = busData.get(0).getStringValue();
      if (!errorString.equals("")) {
        System.err.println("Error getting powerworld bus results: " + errorString);                
      }
      
      ArrayList<ComDataObject> bData = busData.get(1).getArrayValue();                       
      String puString = bData.get(1).getStringValue();
      String angleString = bData.get(2).getStringValue();
      
      double pu = Double.parseDouble(puString.trim());
      double angle = Double.parseDouble(angleString.trim());     
      bus.setPhaseAngle(angle);
      bus.setVoltagePU(pu);    
    }
    
    // get the generator data
    fields = new String[]{PowerworldIOConstants.BUS_NUM, 
    		PowerworldIOConstants.GEN_NUM, 
    		PowerworldIOConstants.GEN_MVAR, 
    		PowerworldIOConstants.GEN_MW, 
    		PowerworldIOConstants.GEN_VOLTAGE}; 
    
    for (Generator generator : model.getGenerators()) {
      Bus bus = model.getNode(generator).getBus();
    	Pair<Integer,Integer> id = powerWorldModel.getGeneratorId(generator);
      String values[] = new String[] {id.getLeft()+"", id.getRight()+"", "", "", ""};
      
      ComDataObject dataObject = powerworld.callData(PowerworldIOConstants.GET_PARAMETERS_SINGLE_ELEMENT, PowerworldIOConstants.GENERATOR, fields, values);
      ArrayList<ComDataObject> genData = dataObject.getArrayValue();
      errorString = genData.get(0).getStringValue();
      if (!errorString.equals("")) {
        System.err.println("Error getting powerworld generator simulation results: " + errorString);                
      }

      ArrayList<ComDataObject> gData = genData.get(1).getArrayValue();                       
      String mvarString = gData.get(2).getStringValue();
      String mwString = gData.get(3).getStringValue();
      String voltageString = gData.get(4).getStringValue();

      double voltage = Double.parseDouble(voltageString.trim());
      double mvar = Double.parseDouble(mvarString.trim());
      double mw = Double.parseDouble(mwString.trim());
      bus.setRemoteVoltagePU(voltage);
//      generator.setDesiredVoltage(voltage);
      generator.setActualRealGeneration(mw);
      generator.setDesiredRealGeneration(mw);
      generator.setActualReactiveGeneration(mvar);
      generator.setDesiredReactiveGeneration(mvar);
    }
    
    return s;
  }

  /**
   * Just testing out some calls...
   * @param args
   */
  public static void main(String[] args) {
	  ComObjectFactory factory = ComObjectUtilities.getDefaultFactory();

	  ComObject comObject = factory.createComObject("pwrworld.SimulatorAuto");
	  comObject.callData("OpenCase", "C:\\Program Files (x86)\\PowerWorld\\Simulator18\\Sample Cases\\b7flat.pwb");    
	  ComDataObject busesObject = comObject.callData("ListOfDevices", "bus", "");
	  ArrayList<ComDataObject> buses = busesObject.getArrayValue();
    String errorString = buses.get(0).getStringValue();
    if (errorString.equals("")) {
      ArrayList<ComDataObject> data = buses.get(1).getArrayValue();
      ArrayList<Integer> ids = data.get(0).getIntArrayValue();
            
      System.out.println("Bus ids");
      for (int i = 0; i < ids.size(); ++i) {
        System.out.println(ids.get(i));
      }
      System.out.println();
      
      
      System.out.println("Bus Data");
      for (int i = 0; i < ids.size(); ++i) {
        ComDataObject dataObject = comObject.callData("GetParametersSingleElement", "bus", new String[]{"pwBusNum", "pwBusName", "pwAreaName", "pwBusPUVolt", "pwBusAngle"}, new String[] {ids.get(i)+"", "", "", "", ""});
        ArrayList<ComDataObject> busData = dataObject.getArrayValue();
        String errorString2 = busData.get(0).getStringValue();
        if (errorString2.equals("")) {
          ArrayList<ComDataObject> bData = busData.get(1).getArrayValue();                       
          String id = bData.get(0).getStringValue();
          System.out.print(id + " ");
          String name = bData.get(1).getStringValue();
          System.out.print(name + " ");
          String areaName = bData.get(2).getStringValue();
          System.out.print(areaName + " ");
          String pu = bData.get(3).getStringValue();
          System.out.print(pu + " ");
          String angle = bData.get(4).getStringValue();
          System.out.print(angle + " ");        
          System.out.println();
        }
        else {
          System.out.println("Error: " + errorString2);                
        }
      }      
    }
    else {
      System.out.println("Error: " + errorString);      
    }
	  
  }
  
}
