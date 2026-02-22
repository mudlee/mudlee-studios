package hu.mudlee.core.render.vulkan;

import static org.lwjgl.glfw.GLFWVulkan.glfwGetRequiredInstanceExtensions;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.EXTDebugUtils.*;
import static org.lwjgl.vulkan.VK12.*;

import hu.mudlee.core.Disposable;
import java.util.HashSet;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class VulkanInstance implements Disposable {

    private static final Logger log = LoggerFactory.getLogger(VulkanInstance.class);
    private static final String VALIDATION_LAYER = "VK_LAYER_KHRONOS_validation";

    private final VkInstance handle;
    private final boolean debug;
    private long debugMessenger = VK_NULL_HANDLE;

    VulkanInstance(String appName, boolean debug) {
        this.debug = debug;

        if (debug && !isValidationLayerAvailable()) {
            log.warn("Validation layer '{}' not found — continuing without it", VALIDATION_LAYER);
        }

        try (MemoryStack stack = stackPush()) {
            var appInfo = VkApplicationInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
                    .pApplicationName(stack.UTF8Safe(appName))
                    .applicationVersion(VK_MAKE_VERSION(1, 0, 0))
                    .pEngineName(stack.UTF8Safe("Mudlee Engine"))
                    .engineVersion(VK_MAKE_VERSION(1, 0, 0))
                    .apiVersion(VK_MAKE_API_VERSION(0, 1, 3, 0));

            var createInfo = VkInstanceCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
                    .pApplicationInfo(appInfo)
                    .ppEnabledExtensionNames(buildExtensionList(stack));

            if (debug && isValidationLayerAvailable()) {
                var layers = stack.mallocPointer(1);
                layers.put(stack.ASCII(VALIDATION_LAYER)).rewind();
                createInfo.ppEnabledLayerNames(layers);

                // Chain debug messenger info so it captures instance creation/destruction messages
                var debugInfo = buildDebugMessengerCreateInfo(stack);
                createInfo.pNext(debugInfo.address());
            }

            var pInstance = stack.mallocPointer(1);
            var result = vkCreateInstance(createInfo, null, pInstance);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to create VkInstance, error: " + result);
            }

            handle = new VkInstance(pInstance.get(0), createInfo);
            log.debug("VkInstance created");

            if (debug && isValidationLayerAvailable()) {
                setupDebugMessenger();
            }
        }
    }

    VkInstance handle() {
        return handle;
    }

    private void setupDebugMessenger() {
        try (MemoryStack stack = stackPush()) {
            var pMessenger = stack.mallocLong(1);
            var result = vkCreateDebugUtilsMessengerEXT(handle, buildDebugMessengerCreateInfo(stack), null, pMessenger);
            if (result != VK_SUCCESS) {
                log.warn("Failed to set up Vulkan debug messenger");
                return;
            }
            debugMessenger = pMessenger.get(0);
            log.debug("Vulkan debug messenger created");
        }
    }

    private VkDebugUtilsMessengerCreateInfoEXT buildDebugMessengerCreateInfo(MemoryStack stack) {
        return VkDebugUtilsMessengerCreateInfoEXT.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DEBUG_UTILS_MESSENGER_CREATE_INFO_EXT)
                .messageSeverity(VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT
                        | VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT
                        | VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT)
                .messageType(VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT
                        | VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT
                        | VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT)
                .pfnUserCallback((severity, types, callbackData, userData) -> {
                    var data = VkDebugUtilsMessengerCallbackDataEXT.create(callbackData);
                    if ((severity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT) != 0) {
                        log.error("[Vulkan Validation] {}", data.pMessageString());
                    } else if ((severity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT) != 0) {
                        log.warn("[Vulkan Validation] {}", data.pMessageString());
                    } else {
                        log.debug("[Vulkan Validation] {}", data.pMessageString());
                    }
                    return VK_FALSE;
                });
    }

    private PointerBuffer buildExtensionList(MemoryStack stack) {
        var glfwExtensions = glfwGetRequiredInstanceExtensions();
        if (glfwExtensions == null) {
            throw new RuntimeException("Failed to get GLFW required Vulkan extensions");
        }

        if (!debug) {
            return glfwExtensions;
        }

        // Append VK_EXT_debug_utils for validation layer messages
        var extensions = stack.mallocPointer(glfwExtensions.remaining() + 1);
        extensions.put(glfwExtensions);
        extensions.put(stack.UTF8(VK_EXT_DEBUG_UTILS_EXTENSION_NAME));
        return extensions.rewind();
    }

    private boolean isValidationLayerAvailable() {
        try (MemoryStack stack = stackPush()) {
            var count = stack.mallocInt(1);
            vkEnumerateInstanceLayerProperties(count, null);

            var layers = VkLayerProperties.malloc(count.get(0), stack);
            vkEnumerateInstanceLayerProperties(count.position(0), layers);

            var names = new HashSet<String>();
            for (VkLayerProperties layer : layers) {
                names.add(layer.layerNameString());
            }
            return names.contains(VALIDATION_LAYER);
        }
    }

    @Override
    public void dispose() {
        if (debug && debugMessenger != VK_NULL_HANDLE) {
            vkDestroyDebugUtilsMessengerEXT(handle, debugMessenger, null);
        }
        vkDestroyInstance(handle, null);
        log.debug("VkInstance destroyed");
    }
}
