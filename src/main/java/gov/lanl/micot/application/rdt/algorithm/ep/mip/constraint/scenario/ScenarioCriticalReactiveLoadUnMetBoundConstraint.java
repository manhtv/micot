package gov.lanl.micot.application.rdt.algorithm.ep.mip.constraint.scenario;

import gov.lanl.micot.infrastructure.ep.model.ElectricPowerModel;
import gov.lanl.micot.infrastructure.ep.model.ElectricPowerNode;
import gov.lanl.micot.infrastructure.ep.model.Load;
import gov.lanl.micot.infrastructure.model.Scenario;
import gov.lanl.micot.infrastructure.optimize.mathprogram.constraint.ScenarioConstraintFactory;
import gov.lanl.micot.application.rdt.algorithm.AlgorithmConstants;
import gov.lanl.micot.application.rdt.algorithm.ep.mip.variable.scenario.ScenarioCriticalReactiveLoadUnMetVariableFactory;
import gov.lanl.micot.application.rdt.algorithm.ep.mip.variable.scenario.ScenarioReactiveLoadPhaseVariableFactory;
import gov.lanl.micot.util.math.solver.LinearConstraint;
import gov.lanl.micot.util.math.solver.LinearConstraintGreaterEq;
import gov.lanl.micot.util.math.solver.Variable;
import gov.lanl.micot.util.math.solver.exception.InvalidConstraintException;
import gov.lanl.micot.util.math.solver.exception.NoVariableException;
import gov.lanl.micot.util.math.solver.exception.VariableExistsException;
import gov.lanl.micot.util.math.solver.mathprogram.MathematicalProgram;

import java.util.Collection;

/**
 * The amount of critical load that is unmet only counts when it is above a threshold, otherwise it is 0
 * 
 * @author Russell Bent
 */
public class ScenarioCriticalReactiveLoadUnMetBoundConstraint extends ScenarioConstraintFactory<ElectricPowerNode, ElectricPowerModel> {

  private double percentage = 0;

  /**
   * Constraint
   */
  public ScenarioCriticalReactiveLoadUnMetBoundConstraint(double percentage, Collection<Scenario> scenarios) {
    super(scenarios);
    this.percentage = percentage;
  }

  /**
   * Get the constraint name
   * 
   * @param load
   * @param phase
   * @return
   */
  private String getConstraintName(Scenario scenario) {
    return "Critical Reactive Load Un Met-" + scenario;
  }

  @Override
  public void constructConstraint(MathematicalProgram problem, ElectricPowerModel model) throws VariableExistsException, NoVariableException, InvalidConstraintException {
    ScenarioReactiveLoadPhaseVariableFactory factory = new ScenarioReactiveLoadPhaseVariableFactory(getScenarios());
    ScenarioCriticalReactiveLoadUnMetVariableFactory vFactory = new ScenarioCriticalReactiveLoadUnMetVariableFactory(getScenarios());
    double mvaBase = model.getMVABase();
    
    
    for (Scenario scenario : getScenarios()) {
      LinearConstraint constraint = new LinearConstraintGreaterEq(getConstraintName(scenario));
      double totalLoad = 0;
      for (ElectricPowerNode node : model.getNodes()) {
        for (Load load : node.getComponents(Load.class)) {
          boolean isCritical = load.getAttribute(AlgorithmConstants.IS_CRITICAL_LOAD_KEY) == null || !load.getAttribute(AlgorithmConstants.IS_CRITICAL_LOAD_KEY, Boolean.class) ? false : true;
          if (!isCritical) {
            continue;
          }

          Variable variable = factory.getVariable(problem, load, ScenarioReactiveLoadPhaseVariableFactory.PHASE_A, scenario);
          if (variable != null) {
            constraint.addVariable(variable, 1.0);
            totalLoad += load.getAttribute(Load.DESIRED_REACTIVE_LOAD_A_KEY, Number.class).doubleValue();
          }

          variable = factory.getVariable(problem, load, ScenarioReactiveLoadPhaseVariableFactory.PHASE_B, scenario);
          if (variable != null) {
            constraint.addVariable(variable, 1.0);
            totalLoad += load.getAttribute(Load.DESIRED_REACTIVE_LOAD_B_KEY, Number.class).doubleValue();
          }

          variable = factory.getVariable(problem, load, ScenarioReactiveLoadPhaseVariableFactory.PHASE_C, scenario);
          if (variable != null) {
            constraint.addVariable(variable, 1.0);
            totalLoad += load.getAttribute(Load.DESIRED_REACTIVE_LOAD_C_KEY, Number.class).doubleValue();
          }
        }
      }

      totalLoad /= mvaBase;
      
      Variable variable = vFactory.getVariable(problem, scenario);
      constraint.addVariable(variable, 1.0);      
      constraint.setRightHandSide(totalLoad * percentage);
      problem.addLinearConstraint(constraint);      
      problem.addBounds(variable, 0, totalLoad * percentage);
    }
  }
 }
