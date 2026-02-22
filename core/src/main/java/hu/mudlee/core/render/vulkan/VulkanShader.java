package hu.mudlee.core.render.vulkan;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK12.*;

import hu.mudlee.core.io.ResourceLoader;
import hu.mudlee.core.render.Shader;
import hu.mudlee.core.render.VertexBufferLayout;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Vulkan shader implementation.
 *
 * <p>Loads SPIR-V bytecode for vertex and fragment stages from classpath resources. The shader name
 * convention maps ".glsl" → ".spv" automatically: Shader.create("vulkan/2d/vert.glsl",
 * "vulkan/2d/frag.glsl") → loads /shaders/vulkan/2d/vert.spv and /shaders/vulkan/2d/frag.spv
 *
 * <p>Pipeline creation is DEFERRED to the first renderRaw() call so that the vertex layout, render
 * pass, and swap chain extent are available (they aren't known at shader construction time). The
 * pipeline is cached and recreated only if the vertex layout changes.
 *
 * <p>Uniforms: "uProjection" and "uView" mat4 values are stored locally and uploaded as push
 * constants (VK_SHADER_STAGE_VERTEX_BIT, 128 bytes total) in renderRaw(). This is the Vulkan best
 * practice for per-draw data that changes every frame. setUniform() for any other name or type is a
 * no-op until the HAL is extended.
 *
 * <p>"TEX_SAMPLER" / createUniform() calls are intentionally ignored — textures are bound via
 * VkDescriptorSets inside VulkanContext.renderRaw().
 *
 * <p>To compile the GLSL sources to SPIR-V: glslc resources/shaders/vulkan/2d/vert.glsl -o
 * resources/shaders/vulkan/2d/vert.spv glslc resources/shaders/vulkan/2d/frag.glsl -o
 * resources/shaders/vulkan/2d/frag.spv
 */
public class VulkanShader extends Shader {

    /** GL_FLOAT value (5126) — kept here to avoid importing OpenGL bindings in a Vulkan class. */
    private static final int GL_FLOAT = 5126;

    /** Push constant size: mat4 projection (64 bytes) + mat4 view (64 bytes). */
    static final int PUSH_CONSTANT_SIZE = 128;

    private static final Logger log = LoggerFactory.getLogger(VulkanShader.class);

    private final VulkanDevice device;
    private final long vertShaderModule;
    private final long fragShaderModule;

    private long descriptorSetLayout = VK_NULL_HANDLE;
    private long pipelineLayout = VK_NULL_HANDLE;

    // Lazily created on first draw; recreated if the vertex layout changes
    private long pipeline = VK_NULL_HANDLE;
    private VertexBufferLayout cachedLayout;

    // Cached matrix values written to push constants in VulkanContext.renderRaw()
    private final float[] projectionData = new float[16];
    private final float[] viewData = new float[16];

    public VulkanShader(String vertexShaderName, String fragmentShaderName) {
        var ctx = VulkanContext.get();
        device = ctx.device();

        // Derive SPIR-V paths from the GLSL names
        var vertPath = "/shaders/" + vertexShaderName.replace(".glsl", ".spv");
        var fragPath = "/shaders/" + fragmentShaderName.replace(".glsl", ".spv");

        vertShaderModule = createShaderModule(vertPath);
        fragShaderModule = createShaderModule(fragPath);

        // Re-use the global layout owned by VulkanContext — no per-shader allocation needed
        descriptorSetLayout = ctx.textureDescriptorSetLayout();
        createPipelineLayout();

        log.debug("VulkanShader created from {} + {}", vertPath, fragPath);
    }

    // -------------------------------------------------------------------------
    // Package-internal API consumed by VulkanContext
    // -------------------------------------------------------------------------

    /**
     * Returns the VkPipeline for the given vertex layout. Creates or recreates the pipeline if the
     * layout changed.
     */
    long getOrCreatePipeline(VertexBufferLayout layout, long renderPass, VkExtent2D extent) {
        if (pipeline == VK_NULL_HANDLE || cachedLayout != layout) {
            if (pipeline != VK_NULL_HANDLE) {
                vkDestroyPipeline(device.device(), pipeline, null);
            }
            pipeline = createGraphicsPipeline(layout, renderPass, extent);
            cachedLayout = layout;
        }
        return pipeline;
    }

    long pipelineLayout() {
        return pipelineLayout;
    }

    long descriptorSetLayout() {
        return descriptorSetLayout;
    }

    float[] projectionData() {
        return projectionData;
    }

    float[] viewData() {
        return viewData;
    }

    // -------------------------------------------------------------------------
    // Shader abstract class implementation
    // -------------------------------------------------------------------------

    @Override
    public int getPipelineId() {
        // Vulkan pipelines are long handles — callers that need the pipeline should cast to
        // VulkanShader
        return 0;
    }

    @Override
    public void bind() {
        // No-op: pipeline binding happens inside vkCmdBindPipeline in VulkanContext.renderRaw()
    }

    @Override
    public void unbind() {
        // No-op
    }

    @Override
    public int getVertexProgramId() {
        return 0;
    }

    @Override
    public int getFragmentProgramId() {
        return 0;
    }

    @Override
    public void createUniform(int programId, String name) {
        // No-op: Vulkan uniforms are push constants or descriptor sets — no named locations
    }

    @Override
    public void setUniform(int programId, String name, Matrix4f value) {
        switch (name) {
            case "uProjection" -> value.get(projectionData);
            case "uView" -> value.get(viewData);
            // Additional mat4 uniforms can be added here when the HAL is extended
        }
    }

    @Override
    public void setUniform(int programId, String name, Vector4f value) {
        // No-op until additional vec4 uniforms are needed
    }

    @Override
    public void setUniform(int programId, String name, float value) {
        // No-op
    }

    @Override
    public void setUniform(int programId, String name, int value) {
        // "TEX_SAMPLER" sampler-unit assignments are meaningless in Vulkan:
        // textures are bound via VkDescriptorSets in VulkanContext.renderRaw()
    }

    @Override
    public void dispose() {
        if (pipeline != VK_NULL_HANDLE) {
            vkDestroyPipeline(device.device(), pipeline, null);
        }
        if (pipelineLayout != VK_NULL_HANDLE) {
            vkDestroyPipelineLayout(device.device(), pipelineLayout, null);
        }
        // descriptorSetLayout is owned by VulkanContext — do NOT destroy it here
        vkDestroyShaderModule(device.device(), fragShaderModule, null);
        vkDestroyShaderModule(device.device(), vertShaderModule, null);
        log.debug("VulkanShader disposed");
    }

    // -------------------------------------------------------------------------
    // Vulkan object creation
    // -------------------------------------------------------------------------

    /**
     * Loads SPIR-V from the classpath and creates a VkShaderModule. Loading and module creation share
     * one MemoryStack frame because ResourceLoader.loadToByteBuffer allocates on the stack.
     */
    private long createShaderModule(String resourcePath) {
        try (MemoryStack stack = stackPush()) {
            var spirvCode = ResourceLoader.loadToByteBuffer(resourcePath, stack);

            var createInfo = VkShaderModuleCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
                    .pCode(spirvCode);

            var pModule = stack.mallocLong(1);
            if (vkCreateShaderModule(device.device(), createInfo, null, pModule) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create VkShaderModule from " + resourcePath);
            }
            return pModule.get(0);
        }
    }

    /**
     * Pipeline layout: one descriptor set for textures (layout from VulkanContext) + 128-byte push
     * constant block for the two transformation matrices.
     */
    private void createPipelineLayout() {
        try (MemoryStack stack = stackPush()) {
            var pushConstantRange = VkPushConstantRange.calloc(1, stack)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT)
                    .offset(0)
                    .size(PUSH_CONSTANT_SIZE);

            var pSetLayouts = stack.longs(descriptorSetLayout);

            var layoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                    .pSetLayouts(pSetLayouts)
                    .pPushConstantRanges(pushConstantRange);

            var pLayout = stack.mallocLong(1);
            if (vkCreatePipelineLayout(device.device(), layoutInfo, null, pLayout) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create VkPipelineLayout");
            }
            pipelineLayout = pLayout.get(0);
        }
    }

    /**
     * Compiles the full VkPipeline for the given vertex layout and render pass. Dynamic viewport and
     * scissor allow the pipeline to work across swapchain recreations.
     */
    private long createGraphicsPipeline(VertexBufferLayout layout, long renderPass, VkExtent2D extent) {
        try (MemoryStack stack = stackPush()) {
            var mainName = stack.UTF8("main");

            var shaderStages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
            shaderStages
                    .get(0)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(VK_SHADER_STAGE_VERTEX_BIT)
                    .module(vertShaderModule)
                    .pName(mainName);
            shaderStages
                    .get(1)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(VK_SHADER_STAGE_FRAGMENT_BIT)
                    .module(fragShaderModule)
                    .pName(mainName);

            // Vertex input: one binding, attributes from the provided VertexBufferLayout
            var attrs = layout.attributes();

            var bindingDesc = VkVertexInputBindingDescription.calloc(1, stack)
                    .binding(0)
                    .stride(attrs.length > 0 ? attrs[0].getStride() : 0)
                    .inputRate(VK_VERTEX_INPUT_RATE_VERTEX);

            var attrDescs = VkVertexInputAttributeDescription.calloc(attrs.length, stack);
            for (int i = 0; i < attrs.length; i++) {
                attrDescs
                        .get(i)
                        .binding(0)
                        .location(attrs[i].getIndex())
                        .format(toVulkanFormat(attrs[i].getDataType(), attrs[i].getDataSize()))
                        .offset(attrs[i].getOffset());
            }

            var vertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
                    .pVertexBindingDescriptions(bindingDesc)
                    .pVertexAttributeDescriptions(attrDescs);

            var inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
                    .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
                    .primitiveRestartEnable(false);

            // Viewport and scissor are dynamic — set each frame in VulkanContext.clear()
            var dynamicState = VkPipelineDynamicStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO)
                    .pDynamicStates(stack.ints(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR));

            var viewportState = VkPipelineViewportStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
                    .viewportCount(1)
                    .scissorCount(1);

            var rasterizer = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
                    .depthClampEnable(false)
                    .rasterizerDiscardEnable(false)
                    .polygonMode(VK_POLYGON_MODE_FILL)
                    .lineWidth(1.0f)
                    .cullMode(VK_CULL_MODE_NONE) // No culling for 2D sprites — back faces may be visible
                    .frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE)
                    .depthBiasEnable(false);

            var multisampling = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
                    .sampleShadingEnable(false)
                    .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);

            // Standard alpha blending
            var colorBlendAttachment = VkPipelineColorBlendAttachmentState.calloc(1, stack)
                    .colorWriteMask(VK_COLOR_COMPONENT_R_BIT
                            | VK_COLOR_COMPONENT_G_BIT
                            | VK_COLOR_COMPONENT_B_BIT
                            | VK_COLOR_COMPONENT_A_BIT)
                    .blendEnable(true)
                    .srcColorBlendFactor(VK_BLEND_FACTOR_SRC_ALPHA)
                    .dstColorBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
                    .colorBlendOp(VK_BLEND_OP_ADD)
                    .srcAlphaBlendFactor(VK_BLEND_FACTOR_ONE)
                    .dstAlphaBlendFactor(VK_BLEND_FACTOR_ZERO)
                    .alphaBlendOp(VK_BLEND_OP_ADD);

            var colorBlending = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
                    .logicOpEnable(false)
                    .pAttachments(colorBlendAttachment);

            var pipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack)
                    .sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
                    .pStages(shaderStages)
                    .pVertexInputState(vertexInput)
                    .pInputAssemblyState(inputAssembly)
                    .pViewportState(viewportState)
                    .pRasterizationState(rasterizer)
                    .pMultisampleState(multisampling)
                    .pColorBlendState(colorBlending)
                    .pDynamicState(dynamicState)
                    .layout(pipelineLayout)
                    .renderPass(renderPass)
                    .subpass(0)
                    .basePipelineHandle(VK_NULL_HANDLE)
                    .basePipelineIndex(-1);

            var pPipeline = stack.mallocLong(1);
            if (vkCreateGraphicsPipelines(device.device(), VK_NULL_HANDLE, pipelineInfo, null, pPipeline)
                    != VK_SUCCESS) {
                throw new RuntimeException("Failed to create VkPipeline");
            }

            log.debug("VkPipeline created for layout {}", layout);
            return pPipeline.get(0);
        }
    }

    /**
     * Converts a GL_FLOAT-typed VertexLayoutAttribute component count to the corresponding VkFormat.
     */
    private int toVulkanFormat(int glDataType, int componentCount) {
        if (glDataType != GL_FLOAT) {
            throw new RuntimeException("Unsupported vertex attribute type (only GL_FLOAT is supported): " + glDataType);
        }
        return switch (componentCount) {
            case 1 -> VK_FORMAT_R32_SFLOAT;
            case 2 -> VK_FORMAT_R32G32_SFLOAT;
            case 3 -> VK_FORMAT_R32G32B32_SFLOAT;
            case 4 -> VK_FORMAT_R32G32B32A32_SFLOAT;
            default -> throw new RuntimeException("Unsupported float component count: " + componentCount);
        };
    }
}
