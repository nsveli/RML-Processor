package be.ugent.mmlab.rml.processor;

import be.ugent.mmlab.rml.condition.model.Condition;
import be.ugent.mmlab.rml.logicalsourcehandler.termmap.TermMapProcessor;

import static be.ugent.mmlab.rml.model.RDFTerm.TermType.BLANK_NODE;

import be.ugent.mmlab.rml.model.PredicateObjectMap;
import be.ugent.mmlab.rml.model.RDFTerm.FunctionTermMap;
import be.ugent.mmlab.rml.model.RDFTerm.GraphMap;
import be.ugent.mmlab.rml.model.RDFTerm.ObjectMap;
import be.ugent.mmlab.rml.model.RDFTerm.SubjectMap;
import be.ugent.mmlab.rml.model.TriplesMap;
import be.ugent.mmlab.rml.model.dataset.RMLDataset;
import be.ugent.mmlab.rml.model.std.StdConditionSubjectMap;
import be.ugent.mmlab.rml.processor.concrete.ConcreteTermMapFactory;
import be.ugent.mmlab.rml.processor.concrete.TermMapProcessorFactory;

import java.util.*;

import be.ugent.mmlab.rml.vocabularies.FnVocabulary;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RML Processor
 *
 * @author andimou
 */
public class StdSubjectMapProcessor implements SubjectMapProcessor {

    // Log
    private static final Logger log =
            LoggerFactory.getLogger(StdSubjectMapProcessor.class.getSimpleName());

    @Override
    public Resource processSubjectMap(RMLDataset dataset, SubjectMap subjectMap,
                                      Object node, RMLProcessor processor) {
        Resource subject = null;
        boolean result;
        SimpleValueFactory vf = SimpleValueFactory.getInstance();

        //Get the uri
        TermMapProcessorFactory factory = new ConcreteTermMapFactory();
        TermMapProcessor termMapProcessor = factory.create(
                subjectMap.getOwnTriplesMap().getLogicalSource().getReferenceFormulation(),
                processor);

        if (subjectMap.getClass().getSimpleName().equals("StdConditionSubjectMap")) {
            log.debug("Processing Conditional Subject Map");
            StdConditionSubjectMap condSubMap =
                    (StdConditionSubjectMap) subjectMap;
            //TODO: Move this to conditionSubjectMapProcessor
            Set<Condition> conditions = condSubMap.getConditions();
            log.debug("Found " + conditions.size() + " conditions");
            ConditionProcessor condProcessor = new StdConditionProcessor();
            result = condProcessor.processConditions(node, termMapProcessor, conditions);
        } else {
            result = true;
        }

        if (result) {
            FunctionTermMap functionTermMap =
                    subjectMap.getFunctionTermMap();
            if (functionTermMap != null) {
                Map<String, Object> parameters = retrieveParameters(node, functionTermMap.getFunctionTriplesMap());

                //parameters = functionTermMap.getParameterRefs();
                String function = functionTermMap.getFunction().toString();

                List<Value> values = termMapProcessor.processFunctionTermMap(
                        functionTermMap, node, function, parameters);

                log.debug("values are " + values);
                Value value = values.get(0);
                try {
                    return (Resource) value;
                } catch (Exception e) {
                    String uri = value.stringValue();
                    if (!uri.startsWith("http://")) {
                        uri = "http://" + uri;
                    }
                    return vf.createIRI(uri);
                }
            }

            List<String> values = termMapProcessor.processTermMap(subjectMap, node);
            //log.info("Abstract RML Processor Graph Map" + subjectMap.getGraphMaps().toString());
            if (values == null || values.isEmpty()) {
                if (subjectMap.getTermType() != BLANK_NODE) {
                    log.debug("No subject was generated for "
                            + subjectMap.getOwnTriplesMap().getName().toString());
                    return null;
                }
            }

            String value = null;
            if (subjectMap.getTermType() != BLANK_NODE) {
                //Since it is the subject, more than one value is not allowed. 
                //Only return the first one. Throw exception if not?
                value = values.get(0);

                if ((value == null) || (value.equals(""))) {
                    log.error("No subject was generated for " + subjectMap.toString());
                    return null;
                }
            }


            //TODO: doublicate code from ObjectMap - they should be handled together
            //TODO:Spring it!
            switch (subjectMap.getTermType()) {
                case IRI:
                    if (value != null && !value.equals("")) {
                        if (value.startsWith("www.")) {
                            value = "http://" + value;
                        }
                    }
                    try {
                        subject = vf.createIRI(value);
                    } catch (Exception e) {
                        return null;
                    }
                    break;
                case BLANK_NODE:
                    subject = vf.createBNode();
                    break;
                default:
                    subject = vf.createIRI(value);
            }
        }
        return subject;
    }

    @Override
    public void processSubjectTypeMap(
            RMLDataset dataset, Resource subject, TriplesMap map, Object node) {
        SubjectMap subjectMap = map.getSubjectMap();
        boolean flag = false;
        SimpleValueFactory vf = SimpleValueFactory.getInstance();
        Set<IRI> classIRIs = subjectMap.getClassIRIs();
        /*String[] vocabs = dataset.getMetadataVocab();
        //TODO: Decide if I keep that here or if I move it to separate class
        if (vocabs != null) {
            if (vocabs.length == 0) {
                flag = true;
            } else {
                for (String vocab : vocabs) {
                    if (vocab.equals("prov")) {
                        flag = true;
                    }
                }
            }
        }*/
        if (subject != null) {
            for (IRI classIRI : classIRIs) {
                if (subjectMap.getGraphMaps().isEmpty()) {
                    //List<Statement> triples =
                    //        dataset.tuplePattern(subject, RDF.TYPE, classIRI);
                    //if (triples.size() == 0) {
                    dataset.add(subject, RDF.TYPE, classIRI);
                    //}
                } else {
                    for (GraphMap graphMap : subjectMap.getGraphMaps()) {
                        if (graphMap.getConstantValue() != null) {
                            dataset.add(
                                    subject, RDF.TYPE, classIRI,
                                    vf.createIRI(graphMap.getConstantValue().toString()));
                        }
                    }
                }
            }
        }
    }

    private Map<String, Object> retrieveParameters(Object node, TriplesMap functionTriplesMap) {
        Map<String, Object> parameters = new HashMap<>();
        TermMapProcessorFactory factory = new ConcreteTermMapFactory();
        TermMapProcessor termMapProcessor =
                factory.create(functionTriplesMap.getLogicalSource().getReferenceFormulation());

        Set<PredicateObjectMap> poms = functionTriplesMap.getPredicateObjectMaps();
        for (PredicateObjectMap pom : poms) {
            Value property = pom.getPredicateMaps().iterator().next().getConstantValue();
            String executes = FnVocabulary.FNO_NAMESPACE + FnVocabulary.FnTerm.EXECUTES;
            if (!property.stringValue().equals(executes)) {
                Value parameter = pom.getPredicateMaps().iterator().next().getConstantValue();
                List<String> valueList = new ArrayList<>();
                Object returnValue;
                SimpleValueFactory vf = SimpleValueFactory.getInstance();
                ObjectMap objectMap = pom.getObjectMaps().iterator().next();
                //A Term map returns one or more values (in case expression matches more)
                if (objectMap != null && !objectMap.getTermType().equals(BLANK_NODE)) {
                    valueList = termMapProcessor.processTermMap(objectMap, node);
                } else {
                    valueList.add(vf.createBNode(null).toString());
                }
                if (valueList == null || valueList.isEmpty()) {
                    // no value is present for this parameter, enter null
                    returnValue = "null"; //TODO wmaroy: change to proper uri for null
                } else if (valueList.size() == 1) {
                    returnValue = valueList.get(0);
                } else {
                    returnValue = valueList;
                }
                parameters.put(parameter.stringValue(), returnValue);
            }
        }

        return parameters;
    }
}
