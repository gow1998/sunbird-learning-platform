package com.ilimi.taxonomy.content.operation.initializer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ilimi.common.dto.Response;
import com.ilimi.common.exception.ClientException;
import com.ilimi.graph.dac.model.Node;
import com.ilimi.taxonomy.content.client.PipelineRequestorClient;
import com.ilimi.taxonomy.content.common.ContentErrorMessageConstants;
import com.ilimi.taxonomy.content.entity.Plugin;
import com.ilimi.taxonomy.content.enums.ContentErrorCodeConstants;
import com.ilimi.taxonomy.content.enums.ContentWorkflowPipelineParams;
import com.ilimi.taxonomy.content.pipeline.finalizer.FinalizePipeline;
import com.ilimi.taxonomy.content.processor.AbstractProcessor;

public class BundleInitializer extends BaseInitializer {		
	
	private static Logger LOGGER = LogManager.getLogger(BundleInitializer.class.getName());
	
	protected String basePath;
	protected String contentId;
	
	private static final String ECML_MIME_TYPE = "application/vnd.ekstep.ecml-archive";

	public BundleInitializer(String basePath, String contentId) {
		if (!isValidBasePath(basePath))
			throw new ClientException(ContentErrorCodeConstants.INVALID_PARAMETER.name(),
					ContentErrorMessageConstants.INVALID_CWP_CONST_PARAM + " | [Path does not Exist.]");
		if (StringUtils.isBlank(contentId))
			throw new ClientException(ContentErrorCodeConstants.INVALID_PARAMETER.name(),
					ContentErrorMessageConstants.INVALID_CWP_CONST_PARAM + " | [Invalid Content Id.]");
		this.basePath = basePath;
		this.contentId = contentId;
	}
	
	@SuppressWarnings("unchecked")
	public Response initialize(Map<String, Object> parameterMap) {
		Response response = new Response();
		LOGGER.info("Fetching the Parameters From BundleInitializer.");
		List<Node> nodes = (List<Node>) parameterMap.get(ContentWorkflowPipelineParams.nodes.name());
		List<Map<String, Object>> contents = (List<Map<String, Object>>) parameterMap.get(ContentWorkflowPipelineParams.Contents.name());
		String bundleFileName = (String) parameterMap.get(ContentWorkflowPipelineParams.bundleFileName.name());
		String manifestVersion = (String) parameterMap.get(ContentWorkflowPipelineParams.manifestVersion.name());
		
		if (null == nodes)
			throw new ClientException(ContentErrorCodeConstants.INVALID_PARAMETER.name(),
					ContentErrorMessageConstants.INVALID_CWP_OP_INIT_PARAM + " | [Invalid or null Node List.]");
		
		LOGGER.info("Total Content To Bundle: " + nodes.size());
		
		Map<String, Object> bundleMap = new HashMap<String, Object>();
		for(Node node: nodes) {
			Map<String, Object> nodeMap = new HashMap<String, Object>();
			
			Boolean ecmlContent = StringUtils.equalsIgnoreCase(ECML_MIME_TYPE, (String) node.getMetadata().get(ContentWorkflowPipelineParams.mimeType.name()));
			ecmlContent = (null == ecmlContent) ? false : ecmlContent;
			
			LOGGER.info("Is ECML Mime-Type? " + ecmlContent);
			
			LOGGER.info("Processing Content Id: " + node.getIdentifier());
			
			// Setting Attribute Value
			this.basePath = getBasePath(node.getIdentifier());
			this.contentId = node.getIdentifier();
			LOGGER.info("Base Path For Content Id '" + this.contentId + "' is " + this.basePath);
			
			// Check if Compression Required
			boolean isCompressRequired = isCompressRequired(node) && ecmlContent;
			
			// Get ECRF Object
			Plugin ecrf = getECRFObject((String) node.getMetadata().get(ContentWorkflowPipelineParams.body.name()));
			
			if (isCompressRequired) {
				// Get Pipeline Object
				AbstractProcessor pipeline = PipelineRequestorClient
						.getPipeline(ContentWorkflowPipelineParams.compress.name(), basePath, contentId);
				
				// Start Pipeline Operation
				ecrf = pipeline.execute(ecrf);
			}
			nodeMap.put(ContentWorkflowPipelineParams.ecrf.name(), ecrf);
			nodeMap.put(ContentWorkflowPipelineParams.isCompressionApplied.name(), isCompressRequired);
			nodeMap.put(ContentWorkflowPipelineParams.basePath.name(), basePath);
			nodeMap.put(ContentWorkflowPipelineParams.node.name(), node);
			nodeMap.put(ContentWorkflowPipelineParams.ecmlType.name(), 
					getECMLType((String) node.getMetadata().get(ContentWorkflowPipelineParams.body.name())));
			bundleMap.put(contentId, nodeMap);
		}
		
		// Call Finalizer
		FinalizePipeline finalize = new FinalizePipeline(basePath, contentId);
		Map<String, Object> finalizeParamMap = new HashMap<String, Object>();
		finalizeParamMap.put(ContentWorkflowPipelineParams.bundleMap.name(), bundleMap);
		finalizeParamMap.put(ContentWorkflowPipelineParams.Contents.name(), contents);
		finalizeParamMap.put(ContentWorkflowPipelineParams.bundleFileName.name(), bundleFileName);
		finalizeParamMap.put(ContentWorkflowPipelineParams.manifestVersion.name(), manifestVersion);
		response = finalize.finalyze(ContentWorkflowPipelineParams.bundle.name(), finalizeParamMap);
		return response;
	}

}
