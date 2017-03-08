package gov.lanl.micot.application.rdt;

import gov.lanl.micot.infrastructure.application.Application;
import gov.lanl.micot.infrastructure.application.ApplicationOutput;
import gov.lanl.micot.infrastructure.ep.model.ElectricPowerModel;
import gov.lanl.micot.infrastructure.ep.model.ElectricPowerNode;
import gov.lanl.micot.infrastructure.optimize.Optimizer;

/**
 * This class defines the resilient distribution design tool as an application
 * @author Russell Bent
 *
 */
public class RDDTApplication implements Application {
  
  public static final String MODEL_FLAG = "Model";
  public static final String OBJECTIVE_FLAG = "Objective";
  public static final String IS_FEASIBLE_FLAG = "IsFeasible";
  
  private ElectricPowerModel model = null;
  private Optimizer<ElectricPowerNode, ElectricPowerModel> algorithm = null;
  
  /**
   * Only one way to instantiate it
   */
  protected RDDTApplication() {    
  }
  
  @Override
  public ApplicationOutput execute() {
    algorithm.solve(model);        
    ApplicationOutput output = new ApplicationOutput();
    output.put(OBJECTIVE_FLAG, algorithm.getObjectiveValue());
    output.put(IS_FEASIBLE_FLAG, algorithm.isFeasible());
    output.put(MODEL_FLAG, model);
    return output;
  }

  /**
   * Set the model
   * @param model
   */
  public void setModel(ElectricPowerModel model) {
    this.model = model;
  }

  public void setAlgorithm(Optimizer<ElectricPowerNode, ElectricPowerModel> optimizer) {
    this.algorithm = optimizer;
  }
  
}