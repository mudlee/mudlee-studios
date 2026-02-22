package hu.mudlee.core.render.vulkan;

import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;

final class VulkanMemoryUtil {

    private VulkanMemoryUtil() {}

    static int findMemoryType(VkPhysicalDeviceMemoryProperties memProperties, int typeFilter, int requiredProperties) {
        for (int i = 0; i < memProperties.memoryTypeCount(); i++) {
            var typeMatch = (typeFilter & (1 << i)) != 0;
            var propertyMatch =
                    (memProperties.memoryTypes(i).propertyFlags() & requiredProperties) == requiredProperties;
            if (typeMatch && propertyMatch) {
                return i;
            }
        }
        throw new RuntimeException("Failed to find suitable GPU memory type");
    }
}
