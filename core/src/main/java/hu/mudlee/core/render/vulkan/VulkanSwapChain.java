package hu.mudlee.core.render.vulkan;

import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.util.vma.Vma.VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE;
import static org.lwjgl.util.vma.Vma.vmaCreateImage;
import static org.lwjgl.util.vma.Vma.vmaDestroyImage;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK12.*;

import hu.mudlee.core.Disposable;
import java.util.HashMap;
import java.util.Map;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.vulkan.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the VkSwapchainKHR and the per-image color/depth attachments + VkFramebuffers.
 */
class VulkanSwapChain implements Disposable {

    private static final Logger log = LoggerFactory.getLogger(VulkanSwapChain.class);

    private final VulkanDevice device;
    private final VulkanAllocator allocator;
    private final long surface;
    private final long windowHandle;

    private long swapChain = VK_NULL_HANDLE;
    private long[] images;
    private long[] imageViews;
    private final Map<VulkanRenderPassSpec, long[]> framebuffersBySpec = new HashMap<>();
    private int imageFormat;
    private int depthFormat = VK_FORMAT_UNDEFINED;
    private VkExtent2D extent;
    private long depthImage = VK_NULL_HANDLE;
    private long depthImageAllocation = VK_NULL_HANDLE;
    private long depthImageView = VK_NULL_HANDLE;

    VulkanSwapChain(
            VulkanDevice device,
            VulkanAllocator allocator,
            VulkanCommandPool commandPool,
            long surface,
            long windowHandle,
            boolean vSync) {
        this.device = device;
        this.allocator = allocator;
        this.surface = surface;
        this.windowHandle = windowHandle;
        create(vSync, VK_NULL_HANDLE);
    }

    private void create(boolean vSync, long oldSwapChain) {
        try (MemoryStack stack = stackPush()) {
            var capabilities = VkSurfaceCapabilitiesKHR.malloc(stack);
            vkGetPhysicalDeviceSurfaceCapabilitiesKHR(device.physicalDevice(), surface, capabilities);

            var surfaceFormat = chooseSurfaceFormat(stack);
            var presentMode = choosePresentMode(stack, vSync);
            var stackExtent = chooseExtent(capabilities, stack);
            if (extent == null) {
                extent = VkExtent2D.malloc();
            }
            extent.width(stackExtent.width()).height(stackExtent.height());
            imageFormat = surfaceFormat.format();

            var imageCount = capabilities.minImageCount() + 1;
            if (capabilities.maxImageCount() > 0 && imageCount > capabilities.maxImageCount()) {
                imageCount = capabilities.maxImageCount();
            }

            var createInfo = VkSwapchainCreateInfoKHR.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR)
                    .surface(surface)
                    .minImageCount(imageCount)
                    .imageFormat(surfaceFormat.format())
                    .imageColorSpace(surfaceFormat.colorSpace())
                    .imageExtent(stackExtent)
                    .imageArrayLayers(1)
                    .imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT);

            var families = device.queueFamilyIndices();
            if (families.graphicsFamily() != families.presentFamily()) {
                var familyIndices = stack.ints(families.graphicsFamily(), families.presentFamily());
                createInfo.imageSharingMode(VK_SHARING_MODE_CONCURRENT).pQueueFamilyIndices(familyIndices);
            } else {
                createInfo.imageSharingMode(VK_SHARING_MODE_EXCLUSIVE);
            }

            createInfo
                    .preTransform(capabilities.currentTransform())
                    .compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
                    .presentMode(presentMode)
                    .clipped(true)
                    .oldSwapchain(oldSwapChain);

            var pSwapChain = stack.mallocLong(1);
            if (vkCreateSwapchainKHR(device.device(), createInfo, null, pSwapChain) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create VkSwapchainKHR");
            }
            swapChain = pSwapChain.get(0);

            var count = stack.mallocInt(1);
            vkGetSwapchainImagesKHR(device.device(), swapChain, count, null);
            var pImages = stack.mallocLong(count.get(0));
            vkGetSwapchainImagesKHR(device.device(), swapChain, count, pImages);

            images = new long[count.get(0)];
            for (int i = 0; i < images.length; i++) {
                images[i] = pImages.get(i);
            }

            createImageViews();
            createDepthResources();
            log.debug("VkSwapchainKHR created ({} images, {}x{})", images.length, extent.width(), extent.height());
        }
    }

    private void createImageViews() {
        imageViews = new long[images.length];
        try (MemoryStack stack = stackPush()) {
            var pView = stack.mallocLong(1);
            for (int i = 0; i < images.length; i++) {
                var viewInfo = VkImageViewCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                        .image(images[i])
                        .viewType(VK_IMAGE_VIEW_TYPE_2D)
                        .format(imageFormat)
                        .components(c -> c.r(VK_COMPONENT_SWIZZLE_IDENTITY)
                                .g(VK_COMPONENT_SWIZZLE_IDENTITY)
                                .b(VK_COMPONENT_SWIZZLE_IDENTITY)
                                .a(VK_COMPONENT_SWIZZLE_IDENTITY))
                        .subresourceRange(r -> r.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                .baseMipLevel(0)
                                .levelCount(1)
                                .baseArrayLayer(0)
                                .layerCount(1));

                if (vkCreateImageView(device.device(), viewInfo, null, pView) != VK_SUCCESS) {
                    throw new RuntimeException("Failed to create VkImageView[" + i + "]");
                }
                imageViews[i] = pView.get(0);
            }
        }
    }

    private void createDepthResources() {
        depthFormat = findDepthFormat();
        try (MemoryStack stack = stackPush()) {
            var imageInfo = VkImageCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
                    .imageType(VK_IMAGE_TYPE_2D)
                    .mipLevels(1)
                    .arrayLayers(1)
                    .format(depthFormat)
                    .tiling(VK_IMAGE_TILING_OPTIMAL)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                    .usage(VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                    .samples(VK_SAMPLE_COUNT_1_BIT);
            imageInfo.extent().width(extent.width()).height(extent.height()).depth(1);

            var allocInfo = VmaAllocationCreateInfo.calloc(stack).usage(VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE);
            var pImage = stack.mallocLong(1);
            var pAlloc = stack.mallocPointer(1);
            if (vmaCreateImage(allocator.handle(), imageInfo, allocInfo, pImage, pAlloc, null) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create swapchain depth image");
            }
            depthImage = pImage.get(0);
            depthImageAllocation = pAlloc.get(0);

            var viewInfo = VkImageViewCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                    .image(depthImage)
                    .viewType(VK_IMAGE_VIEW_TYPE_2D)
                    .format(depthFormat);
            viewInfo.subresourceRange()
                    .aspectMask(
                            hasStencilComponent(depthFormat)
                                    ? VK_IMAGE_ASPECT_DEPTH_BIT | VK_IMAGE_ASPECT_STENCIL_BIT
                                    : VK_IMAGE_ASPECT_DEPTH_BIT)
                    .baseMipLevel(0)
                    .levelCount(1)
                    .baseArrayLayer(0)
                    .layerCount(1);

            var pView = stack.mallocLong(1);
            if (vkCreateImageView(device.device(), viewInfo, null, pView) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create swapchain depth image view");
            }
            depthImageView = pView.get(0);
        }
    }

    long framebuffer(int index, VulkanRenderPassSpec spec, long renderPass) {
        var framebuffers = framebuffersBySpec.computeIfAbsent(spec, ignored -> createFramebuffers(spec, renderPass));
        return framebuffers[index];
    }

    private long[] createFramebuffers(VulkanRenderPassSpec spec, long renderPass) {
        var framebuffers = new long[imageViews.length];
        try (MemoryStack stack = stackPush()) {
            var pFramebuffer = stack.mallocLong(1);
            for (int i = 0; i < imageViews.length; i++) {
                var attachments = spec.hasDepthAttachment()
                        ? stack.longs(imageViews[i], depthImageView)
                        : stack.longs(imageViews[i]);
                var framebufferInfo = VkFramebufferCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO)
                        .renderPass(renderPass)
                        .pAttachments(attachments)
                        .width(extent.width())
                        .height(extent.height())
                        .layers(1);

                if (vkCreateFramebuffer(device.device(), framebufferInfo, null, pFramebuffer) != VK_SUCCESS) {
                    throw new RuntimeException("Failed to create VkFramebuffer[" + i + "]");
                }
                framebuffers[i] = pFramebuffer.get(0);
            }
        }
        return framebuffers;
    }

    void recreate(boolean vSync) {
        var oldSwapChain = swapChain;
        destroyFramebuffers();
        destroyDepthResources();
        destroyImageViews();
        create(vSync, oldSwapChain);
        if (oldSwapChain != VK_NULL_HANDLE) {
            vkDestroySwapchainKHR(device.device(), oldSwapChain, null);
        }
        log.debug("VkSwapchainKHR recreated");
    }

    int imageFormat() {
        return imageFormat;
    }

    int depthFormat() {
        return depthFormat;
    }

    VkExtent2D extent() {
        return extent;
    }

    int imageCount() {
        return images.length;
    }

    long swapChainHandle() {
        return swapChain;
    }

    private VkSurfaceFormatKHR chooseSurfaceFormat(MemoryStack stack) {
        var count = stack.mallocInt(1);
        vkGetPhysicalDeviceSurfaceFormatsKHR(device.physicalDevice(), surface, count, null);
        var formats = VkSurfaceFormatKHR.malloc(count.get(0), stack);
        vkGetPhysicalDeviceSurfaceFormatsKHR(device.physicalDevice(), surface, count, formats);

        for (VkSurfaceFormatKHR format : formats) {
            if (format.format() == VK_FORMAT_B8G8R8A8_SRGB
                    && format.colorSpace() == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
                return format;
            }
        }
        return formats.get(0);
    }

    private int choosePresentMode(MemoryStack stack, boolean vSync) {
        if (vSync) {
            return VK_PRESENT_MODE_FIFO_KHR;
        }

        var count = stack.mallocInt(1);
        vkGetPhysicalDeviceSurfacePresentModesKHR(device.physicalDevice(), surface, count, null);
        var modes = stack.mallocInt(count.get(0));
        vkGetPhysicalDeviceSurfacePresentModesKHR(device.physicalDevice(), surface, count, modes);

        var hasMailbox = false;
        var hasImmediate = false;
        for (int i = 0; i < count.get(0); i++) {
            if (modes.get(i) == VK_PRESENT_MODE_MAILBOX_KHR) {
                hasMailbox = true;
            }
            if (modes.get(i) == VK_PRESENT_MODE_IMMEDIATE_KHR) {
                hasImmediate = true;
            }
        }

        if (hasMailbox) {
            return VK_PRESENT_MODE_MAILBOX_KHR;
        }
        if (hasImmediate) {
            log.warn("MAILBOX not available, falling back to IMMEDIATE");
            return VK_PRESENT_MODE_IMMEDIATE_KHR;
        }
        log.warn("MAILBOX and IMMEDIATE not available, falling back to FIFO (vSync effectively forced on)");
        return VK_PRESENT_MODE_FIFO_KHR;
    }

    private VkExtent2D chooseExtent(VkSurfaceCapabilitiesKHR capabilities, MemoryStack stack) {
        if (capabilities.currentExtent().width() != 0xFFFFFFFF) {
            return capabilities.currentExtent();
        }

        var width = stack.mallocInt(1);
        var height = stack.mallocInt(1);
        glfwGetFramebufferSize(windowHandle, width, height);

        return VkExtent2D.malloc(stack)
                .width(Math.clamp(
                        width.get(0),
                        capabilities.minImageExtent().width(),
                        capabilities.maxImageExtent().width()))
                .height(Math.clamp(
                        height.get(0),
                        capabilities.minImageExtent().height(),
                        capabilities.maxImageExtent().height()));
    }

    private int findDepthFormat() {
        return findSupportedFormat(
                VK_IMAGE_TILING_OPTIMAL,
                VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT,
                VK_FORMAT_D32_SFLOAT,
                VK_FORMAT_D32_SFLOAT_S8_UINT,
                VK_FORMAT_D24_UNORM_S8_UINT);
    }

    private int findSupportedFormat(int tiling, int features, int... candidates) {
        try (MemoryStack stack = stackPush()) {
            var props = VkFormatProperties.malloc(stack);
            for (var format : candidates) {
                vkGetPhysicalDeviceFormatProperties(device.physicalDevice(), format, props);
                var supported = tiling == VK_IMAGE_TILING_LINEAR
                        ? (props.linearTilingFeatures() & features) == features
                        : (props.optimalTilingFeatures() & features) == features;
                if (supported) {
                    return format;
                }
            }
        }
        throw new RuntimeException("Failed to find a supported swapchain depth format");
    }

    private boolean hasStencilComponent(int format) {
        return format == VK_FORMAT_D32_SFLOAT_S8_UINT || format == VK_FORMAT_D24_UNORM_S8_UINT;
    }

    private void destroyFramebuffers() {
        for (var framebuffers : framebuffersBySpec.values()) {
            for (long fb : framebuffers) {
                vkDestroyFramebuffer(device.device(), fb, null);
            }
        }
        framebuffersBySpec.clear();
    }

    private void destroyImageViews() {
        if (imageViews != null) {
            for (long view : imageViews) {
                vkDestroyImageView(device.device(), view, null);
            }
            imageViews = null;
        }
    }

    private void destroyDepthResources() {
        if (depthImageView != VK_NULL_HANDLE) {
            vkDestroyImageView(device.device(), depthImageView, null);
            depthImageView = VK_NULL_HANDLE;
        }
        if (depthImage != VK_NULL_HANDLE) {
            vmaDestroyImage(allocator.handle(), depthImage, depthImageAllocation);
            depthImage = VK_NULL_HANDLE;
            depthImageAllocation = VK_NULL_HANDLE;
        }
        depthFormat = VK_FORMAT_UNDEFINED;
    }

    @Override
    public void dispose() {
        destroyFramebuffers();
        destroyDepthResources();
        destroyImageViews();
        vkDestroySwapchainKHR(device.device(), swapChain, null);
        if (extent != null) {
            extent.free();
        }
        log.debug("VkSwapchainKHR destroyed");
    }
}
