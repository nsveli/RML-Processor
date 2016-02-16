package be.ugent.mmlab.rml.processor;

import be.ugent.mmlab.rml.logicalsourcehandler.termmap.TermMapProcessor;
import be.ugent.mmlab.rml.metadata.MetadataGenerator;
import be.ugent.mmlab.rml.model.RDFTerm.GraphMap;
import be.ugent.mmlab.rml.model.RDFTerm.SubjectMap;
import be.ugent.mmlab.rml.model.dataset.MetadataRMLDataset;
import be.ugent.mmlab.rml.model.dataset.RMLDataset;
import java.util.List;
import java.util.Set;
import org.openrdf.model.Resource;
import org.openrdf.model.Statement;
import org.openrdf.model.impl.URIImpl;
import org.openrdf.model.vocabulary.RDF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RML Processor
 *
 * @author andimou
 */
public class MetadataSubjectMapProcessor extends StdSubjectMapProcessor implements SubjectMapProcessor {
    private TermMapProcessor termMapProcessor ;
    
    // Log
    private static final Logger log = 
            LoggerFactory.getLogger(MetadataSubjectMapProcessor.class);
    
    
    @Override
    public void processSubjectTypeMap(RMLDataset originalDataset, 
    Resource subject, SubjectMap subjectMap, Object node) {
        MetadataGenerator metadataGenerator = new MetadataGenerator();
               
        boolean flag = false;
        Set<org.openrdf.model.URI> classIRIs = subjectMap.getClassIRIs();
        MetadataRMLDataset dataset = (MetadataRMLDataset) originalDataset ;
        
        List vocabs = dataset.getMetadataVocab();
        //TODO: Decide if I keep that here or if I move it to separate class
        if (vocabs != null) {
            if (vocabs.isEmpty()) {
                flag = true;
            } else {
                for (Object vocab : vocabs) {
                    if (vocab.toString().equals("prov")) {
                        flag = true;
                    }
                }
            }
        }
        if (subject != null) {
            for (org.openrdf.model.URI classIRI : classIRIs) {
                if (subjectMap.getGraphMaps().isEmpty()) {
                    List<Statement> triples =
                            dataset.tuplePattern(subject, RDF.TYPE, classIRI);
                    if (triples.size() == 0) {
                        dataset.add(subject, RDF.TYPE, classIRI);

                        if (dataset.getMetadataLevel().equals("triple")) {
                            if (flag == true) {
                                RMLDataset metadata = dataset.getMetadataDataset();
                                metadataGenerator.generateTripleMetaData(dataset,
                                        subject, RDF.TYPE, classIRI);
                            }

                        }

                    }
                } else {
                    for (GraphMap map : subjectMap.getGraphMaps()) {
                        if (map.getConstantValue() != null) {
                            dataset.add(
                                    subject, RDF.TYPE, classIRI,
                                    new URIImpl(map.getConstantValue().toString()));
                        }
                    }
                }
            }
        }
    }
}
