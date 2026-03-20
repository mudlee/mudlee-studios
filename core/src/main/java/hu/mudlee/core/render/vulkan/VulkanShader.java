package hu.mudlee.core.render.vulkan;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.system.MemoryUtil.memFree;
import static org.lwjgl.util.shaderc.Shaderc.*;
import static org.lwjgl.vulkan.VK12.*;

import hu.mudlee.core.io.ResourceLoader;
import hu.mudlee.core.render.Shader;
import hu.mudlee.core.render.VertexBufferLayout;
import hu.mudlee.core.render.VertexInputRate;
import hu.mudlee.core.render.types.ShaderConfig;
import hu.mudlee.core.render.types.ShaderCullMode;
import hu.mudlee.core.render.types.ShaderTypes;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Vulkan shader implementation.
 *
 * <p>Loads SPIR-V bytecode for vertex and fragment stages from classpath resources. The shader name
 * convention maps ".glsl" → ".spv" automatically: Shader.create("vulkan/2d/vert.glsl",
 * "vulkan/2d/frag.glsl") → loads /shaders/vulkan/2d/vert.spv and /shaders/vulkan/2d/frag.spv. If
 * the SPIR-V resource is missing, the GLSL source is compiled at runtime via shaderc.
 *
 * <p>Pipeline creation is DEFERRED to the first renderRaw() call so that the vertex layout, render
 * pass, and swap chain extent are available (they aren't known at shader construction time). The
 * pipeline is cached by (vertexLayout, renderPass) and recreated when either changes, so the same
 * shader can correctly target both the swapchain backbuffer and off-screen VulkanRenderTargets.
 *
 * <p>Uniforms: "uProjection" and "uView" mat4 values are always stored locally and uploaded as push
 * constants (VK_SHADER_STAGE_VERTEX_BIT, 128 bytes total) in renderRaw(). 3D shaders may also opt
 * into a "uModel" mat4, expanding the push constant block to 192 bytes when the device supports it.
 *
 * <p>Textures are bound via VkDescriptorSets inside VulkanContext.renderRaw().
 *
 * <p>To compile the GLSL sources to SPIR-V: glslc resources/shaders/vulkan/2d/vert.glsl -o
 * resources/shaders/vulkan/2d/vert.spv glslc resources/shaders/vulkan/2d/frag.glsl -o
 * resources/shaders/vulkan/2d/frag.spv
 */
public class VulkanShader extends Shader {

    private static final int MATRIX_FLOAT_COUNT = 16;
    private static final int PROJECTION_AND_VIEW_FLOAT_COUNT = MATRIX_FLOAT_COUNT * 2;
    private static final int PROJECTION_AND_VIEW_PUSH_CONSTANT_SIZE = PROJECTION_AND_VIEW_FLOAT_COUNT * Float.BYTES;
    private static final int MODEL_FLOAT_COUNT = MATRIX_FLOAT_COUNT;

    private static final Logger log = LoggerFactory.getLogger(VulkanShader.class);

    private final VulkanContext context;
    private final VulkanDevice device;
    private final ShaderConfig config;
    private final long vertShaderModule;
    private final long fragShaderModule;

    private long descriptorSetLayout = VK_NULL_HANDLE;
    private long pipelineLayout = VK_NULL_HANDLE;

    private final Map<PipelineKey, Long> pipelines = new HashMap<>();

    // Cached matrix values written to push constants in VulkanContext.renderRaw()
    private final float[] projectionData = new float[16];
    private final float[] viewData = new float[16];
    private final float[] modelData = new Matrix4f().identity().get(new float[16]);

    public VulkanShader(String vertexShaderName, String fragmentShaderName, ShaderConfig config) {
        context = VulkanContext.get();
        device = context.device();
        this.config = config;

        // Derive SPIR-V paths from the GLSL names
        var vertPath = "/shaders/" + vertexShaderName.replace(".glsl", ".spv");
        var fragPath = "/shaders/" + fragmentShaderName.replace(".glsl", ".spv");

        vertShaderModule = createShaderModule(vertPath);
        fragShaderModule = createShaderModule(fragPath);

        // Re-use the global layout owned by VulkanContext — no per-shader allocation needed
        descriptorSetLayout = context.textureDescriptorSetLayout();
        createPipelineLayout();
        context.registerShader(this);

        log.debug("VulkanShader created from {} + {} with config {}", vertPath, fragPath, config);
    }

    // -------------------------------------------------------------------------
    // Package-internal API consumed by VulkanContext
    // -------------------------------------------------------------------------

    /**
     * Returns the VkPipeline for the given vertex buffer bindings and render pass. Creates and caches
     * pipelines by stable signature so multiple layouts and passes can coexist.
     */
    long getOrCreatePipeline(List<VulkanVertexBuffer> vertexBuffers, long renderPass, VkExtent2D extent) {
        var layouts = vertexBuffers.stream().map(VulkanVertexBuffer::getLayout).toList();
        var key = new PipelineKey(layouts, renderPass);
        var pipeline = pipelines.get(key);
        if (pipeline == null) {
            pipeline = createGraphicsPipeline(layouts, renderPass, extent);
            pipelines.put(key, pipeline);
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

    float[] modelData() {
        return modelData;
    }

    int pushConstantFloatCount() {
        return config.usesModelMatrix()
                ? PROJECTION_AND_VIEW_FLOAT_COUNT + MODEL_FLOAT_COUNT
                : PROJECTION_AND_VIEW_FLOAT_COUNT;
    }

    // -------------------------------------------------------------------------
    // Shader abstract class implementation
    // -------------------------------------------------------------------------

    @Override
    public void setUniform(String name, Matrix4f value) {
        switch (name) {
            case "uProjection" -> value.get(projectionData);
            case "uView" -> value.get(viewData);
            case "uModel" -> value.get(modelData);
        }
    }

    @Override
    public void dispose() {
        context.unregisterShader(this);
        var pipelinesToDestroy = List.copyOf(pipelines.values());
        pipelines.clear();
        var pipelineLayoutToDestroy = pipelineLayout;
        pipelineLayout = VK_NULL_HANDLE;
        context.deferRelease(() -> {
            for (var pipeline : pipelinesToDestroy) {
                vkDestroyPipeline(device.device(), pipeline, null);
            }
            if (pipelineLayoutToDestroy != VK_NULL_HANDLE) {
                vkDestroyPipelineLayout(device.device(), pipelineLayoutToDestroy, null);
            }
            vkDestroyShaderModule(device.device(), fragShaderModule, null);
            vkDestroyShaderModule(device.device(), vertShaderModule, null);
        });
        log.debug("VulkanShader disposed");
    }

    // -------------------------------------------------------------------------
    // Vulkan object creation
    // -------------------------------------------------------------------------

    /**
     * Loads SPIR-V from the classpath and creates a VkShaderModule. Falls back to compiling the GLSL
     * source at runtime when no precompiled SPIR-V resource exists.
     */
    private long createShaderModule(String resourcePath) {
        ByteBuffer spirvCode = null;
        try (MemoryStack stack = stackPush()) {
            spirvCode = loadOrCompileSpirv(resourcePath);

            var createInfo = VkShaderModuleCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
                    .pCode(spirvCode);

            var pModule = stack.mallocLong(1);
            if (vkCreateShaderModule(device.device(), createInfo, null, pModule) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create VkShaderModule from " + resourcePath);
            }
            return pModule.get(0);
        } finally {
            if (spirvCode != null) {
                memFree(spirvCode);
            }
        }
    }

    private ByteBuffer loadOrCompileSpirv(String resourcePath) {
        if (ResourceLoader.class.getResource(resourcePath) != null) {
            return ResourceLoader.loadToDirectByteBuffer(resourcePath);
        }

        var sourcePath = resourcePath.replace(".spv", ".glsl");
        log.info("Precompiled shader {} not found, compiling {} at runtime", resourcePath, sourcePath);
        var source = ResourceLoader.load(sourcePath);
        return compileGlslToSpirv(sourcePath, source, shaderKindFor(sourcePath));
    }

    private ByteBuffer compileGlslToSpirv(String sourcePath, String source, int shaderKind) {
        var compiler = shaderc_compiler_initialize();
        if (compiler == NULL) {
            throw new IllegalStateException("Failed to initialise shaderc compiler");
        }

        var options = shaderc_compile_options_initialize();
        if (options == NULL) {
            shaderc_compiler_release(compiler);
            throw new IllegalStateException("Failed to initialise shaderc compile options");
        }

        try {
            shaderc_compile_options_set_target_env(options, shaderc_target_env_vulkan, shaderc_env_version_vulkan_1_2);
            shaderc_compile_options_set_source_language(options, shaderc_source_language_glsl);

            var result = shaderc_compile_into_spv(compiler, source, shaderKind, sourcePath, "main", options);
            if (result == NULL) {
                throw new IllegalStateException("shaderc returned a null compilation result for " + sourcePath);
            }

            try {
                var status = shaderc_result_get_compilation_status(result);
                if (status != shaderc_compilation_status_success) {
                    throw new IllegalStateException(
                            "Failed to compile shader " + sourcePath + ": " + shaderc_result_get_error_message(result));
                }
                var bytes = shaderc_result_get_bytes(result);
                if (bytes == null) {
                    throw new IllegalStateException("shaderc returned no SPIR-V bytes for " + sourcePath);
                }
                var copy = org.lwjgl.system.MemoryUtil.memAlloc(bytes.remaining());
                copy.put(bytes).flip();
                return copy;
            } finally {
                shaderc_result_release(result);
            }
        } finally {
            shaderc_compile_options_release(options);
            shaderc_compiler_release(compiler);
        }
    }

    private int shaderKindFor(String sourcePath) {
        if (sourcePath.endsWith("vert.glsl")) {
            return shaderc_glsl_vertex_shader;
        }
        if (sourcePath.endsWith("frag.glsl")) {
            return shaderc_glsl_fragment_shader;
        }
        throw new IllegalArgumentException("Unsupported shader stage for source path: " + sourcePath);
    }

    /**
     * Pipeline layout: one descriptor set for textures (layout from VulkanContext) + a push constant
     * block for projection/view, optionally extended with a model matrix for 3D shaders.
     */
    private void createPipelineLayout() {
        var pushConstantSize = pushConstantSizeBytes();
        validatePushConstantSize(pushConstantSize);
        try (MemoryStack stack = stackPush()) {
            var pushConstantRange = VkPushConstantRange.calloc(1, stack)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT)
                    .offset(0)
                    .size(pushConstantSize);

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
    private long createGraphicsPipeline(List<VertexBufferLayout> layouts, long renderPass, VkExtent2D extent) {
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

            var totalAttributeCount = layouts.stream()
                    .mapToInt(layout -> layout.attributes().length)
                    .sum();
            var bindingDescs = VkVertexInputBindingDescription.calloc(layouts.size(), stack);
            var attrDescs = VkVertexInputAttributeDescription.calloc(totalAttributeCount, stack);

            var attributeIndex = 0;
            for (int bindingIndex = 0; bindingIndex < layouts.size(); bindingIndex++) {
                var layout = layouts.get(bindingIndex);
                bindingDescs
                        .get(bindingIndex)
                        .binding(bindingIndex)
                        .stride(layout.stride())
                        .inputRate(toVulkanInputRate(layout.inputRate()));

                for (var attr : layout.attributes()) {
                    attrDescs
                            .get(attributeIndex++)
                            .binding(bindingIndex)
                            .location(attr.getIndex())
                            .format(toVulkanFormat(attr.getDataType(), attr.getDataSize(), attr.isNormalized()))
                            .offset(attr.getOffset());
                }
            }

            var vertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
                    .pVertexBindingDescriptions(bindingDescs)
                    .pVertexAttributeDescriptions(attrDescs);

            var inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
                    .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
                    .primitiveRestartEnable(false);

            // Viewport and scissor are dynamic — set each frame in VulkanContext.beginFrame()
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
                    .cullMode(toVulkanCullMode(config.cullMode()))
                    .frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE)
                    .depthBiasEnable(false);

            var multisampling = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
                    .sampleShadingEnable(false)
                    .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);

            var colorBlendAttachment = VkPipelineColorBlendAttachmentState.calloc(1, stack)
                    .colorWriteMask(VK_COLOR_COMPONENT_R_BIT
                            | VK_COLOR_COMPONENT_G_BIT
                            | VK_COLOR_COMPONENT_B_BIT
                            | VK_COLOR_COMPONENT_A_BIT)
                    .blendEnable(config.blendingEnabled())
                    .srcColorBlendFactor(config.blendingEnabled() ? VK_BLEND_FACTOR_SRC_ALPHA : VK_BLEND_FACTOR_ONE)
                    .dstColorBlendFactor(
                            config.blendingEnabled() ? VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA : VK_BLEND_FACTOR_ZERO)
                    .colorBlendOp(VK_BLEND_OP_ADD)
                    .srcAlphaBlendFactor(VK_BLEND_FACTOR_ONE)
                    .dstAlphaBlendFactor(VK_BLEND_FACTOR_ZERO)
                    .alphaBlendOp(VK_BLEND_OP_ADD);

            var colorBlending = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
                    .logicOpEnable(false)
                    .pAttachments(colorBlendAttachment);

            var depthStencil = VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO)
                    .depthTestEnable(config.depthTestEnabled())
                    .depthWriteEnable(config.depthWriteEnabled())
                    .depthCompareOp(VK_COMPARE_OP_LESS)
                    .depthBoundsTestEnable(false)
                    .stencilTestEnable(false);

            var pipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack)
                    .sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
                    .pStages(shaderStages)
                    .pVertexInputState(vertexInput)
                    .pInputAssemblyState(inputAssembly)
                    .pViewportState(viewportState)
                    .pRasterizationState(rasterizer)
                    .pMultisampleState(multisampling)
                    .pDepthStencilState(depthStencil)
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

            log.debug("VkPipeline created for layouts {}", layouts);
            return pPipeline.get(0);
        }
    }

    void invalidatePipeline() {
        for (var pipeline : pipelines.values()) {
            vkDestroyPipeline(device.device(), pipeline, null);
        }
        pipelines.clear();
    }

    private int toVulkanInputRate(VertexInputRate inputRate) {
        return switch (inputRate) {
            case PER_VERTEX -> VK_VERTEX_INPUT_RATE_VERTEX;
            case PER_INSTANCE -> VK_VERTEX_INPUT_RATE_INSTANCE;
        };
    }

    private int toVulkanCullMode(ShaderCullMode cullMode) {
        return switch (cullMode) {
            case NONE -> VK_CULL_MODE_NONE;
            case BACK -> VK_CULL_MODE_BACK_BIT;
            case FRONT -> VK_CULL_MODE_FRONT_BIT;
        };
    }

    private int pushConstantSizeBytes() {
        return pushConstantFloatCount() * Float.BYTES;
    }

    private void validatePushConstantSize(int requiredSize) {
        try (MemoryStack stack = stackPush()) {
            var props = VkPhysicalDeviceProperties.malloc(stack);
            vkGetPhysicalDeviceProperties(device.physicalDevice(), props);
            var maxPushConstantsSize = props.limits().maxPushConstantsSize();
            if (requiredSize > maxPushConstantsSize) {
                throw new IllegalStateException("Shader requires "
                        + requiredSize
                        + " bytes of push constants, but GPU only supports "
                        + maxPushConstantsSize);
            }
        }
    }

    private int toVulkanFormat(ShaderTypes dataType, int componentCount, boolean normalized) {
        return switch (dataType) {
            case FLOAT ->
                switch (componentCount) {
                    case 1 -> VK_FORMAT_R32_SFLOAT;
                    case 2 -> VK_FORMAT_R32G32_SFLOAT;
                    case 3 -> VK_FORMAT_R32G32B32_SFLOAT;
                    case 4 -> VK_FORMAT_R32G32B32A32_SFLOAT;
                    default -> throw new RuntimeException("Unsupported float component count: " + componentCount);
                };
            case UNSIGNED_BYTE ->
                switch (componentCount) {
                    case 1 -> normalized ? VK_FORMAT_R8_UNORM : VK_FORMAT_R8_UINT;
                    case 2 -> normalized ? VK_FORMAT_R8G8_UNORM : VK_FORMAT_R8G8_UINT;
                    case 3 -> normalized ? VK_FORMAT_R8G8B8_UNORM : VK_FORMAT_R8G8B8_UINT;
                    case 4 -> normalized ? VK_FORMAT_R8G8B8A8_UNORM : VK_FORMAT_R8G8B8A8_UINT;
                    default ->
                        throw new RuntimeException("Unsupported unsigned byte component count: " + componentCount);
                };
        };
    }

    private record PipelineKey(List<VertexBufferLayout> layouts, long renderPass) {}
}
