package hu.mudlee.core.render.vulkan;

import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK12.*;

import hu.mudlee.core.Disposable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the VkSwapchainKHR and the per-image VkImageViews + VkFramebuffers.
 *
 * <p>Call {@link #buildFramebuffers(long)} after the render pass is created. Call {@link
 * #recreate(long, long, boolean)} on window resize.
 */
class VulkanSwapChain implements Disposable {

    private static final Logger log = LoggerFactory.getLogger(VulkanSwapChain.class);

    private final VulkanDevice device;
    private final long surface;
    private final long windowHandle;

    private long swapChain = VK_NULL_HANDLE;
    private long[] images;
    private long[] imageViews;
    private long[] framebuffers;
    private int imageFormat;
    private VkExtent2D extent;

    VulkanSwapChain(VulkanDevice device, long surface, long windowHandle, boolean vSync) {
        this.device = device;
        this.surface = surface;
        this.windowHandle = windowHandle;
        create(vSync);
    }

    private void create(boolean vSync) {
        try (MemoryStack stack = stackPush()) {
            var capabilities = VkSurfaceCapabilitiesKHR.malloc(stack);
            vkGetPhysicalDeviceSurfaceCapabilitiesKHR(device.physicalDevice(), surface, capabilities);

            var surfaceFormat = chooseSurfaceFormat(stack);
            var presentMode = choosePresentMode(stack, vSync);
            // chooseExtent() returns a stack-allocated struct — copy to heap before the frame closes
            var stackExtent = chooseExtent(capabilities, stack);
            if (extent == null) {
                extent = VkExtent2D.malloc();
            }
            extent.width(stackExtent.width()).height(stackExtent.height());
            imageFormat = surfaceFormat.format();

            // One more image than the minimum gives the driver room to breathe without stalling us
            var imageCount = capabilities.minImageCount() + 1;
            if (capabilities.maxImageCount() > 0) {
                imageCount = Math.min(imageCount, capabilities.maxImageCount());
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
                    .oldSwapchain(VK_NULL_HANDLE);

            var pSwapChain = stack.mallocLong(1);
            if (vkCreateSwapchainKHR(device.device(), createInfo, null, pSwapChain) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create VkSwapchainKHR");
            }
            swapChain = pSwapChain.get(0);

            // Retrieve the swap chain images
            var count = stack.mallocInt(1);
            vkGetSwapchainImagesKHR(device.device(), swapChain, count, null);
            var pImages = stack.mallocLong(count.get(0));
            vkGetSwapchainImagesKHR(device.device(), swapChain, count, pImages);

            images = new long[count.get(0)];
            for (int i = 0; i < images.length; i++) {
                images[i] = pImages.get(i);
            }

            createImageViews();
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

    void buildFramebuffers(long renderPass) {
        framebuffers = new long[imageViews.length];
        try (MemoryStack stack = stackPush()) {
            var pFramebuffer = stack.mallocLong(1);
            for (int i = 0; i < imageViews.length; i++) {
                var attachments = stack.longs(imageViews[i]);
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
    }

    void recreate(long renderPass, boolean vSync) {
        destroyFramebuffers();
        destroyImageViews();
        vkDestroySwapchainKHR(device.device(), swapChain, null);
        create(vSync);
        buildFramebuffers(renderPass);
        log.debug("VkSwapchainKHR recreated");
    }

    int imageFormat() {
        return imageFormat;
    }

    VkExtent2D extent() {
        return extent;
    }

    int imageCount() {
        return images.length;
    }

    long framebuffer(int index) {
        return framebuffers[index];
    }

    long swapChainHandle() {
        return swapChain;
    }

    private VkSurfaceFormatKHR chooseSurfaceFormat(MemoryStack stack) {
        var count = stack.mallocInt(1);
        vkGetPhysicalDeviceSurfaceFormatsKHR(device.physicalDevice(), surface, count, null);
        var formats = VkSurfaceFormatKHR.malloc(count.get(0), stack);
        vkGetPhysicalDeviceSurfaceFormatsKHR(device.physicalDevice(), surface, count, formats);

        // Prefer BGRA8 sRGB — most common, best perceptual quality
        for (VkSurfaceFormatKHR format : formats) {
            if (format.format() == VK_FORMAT_B8G8R8A8_SRGB
                    && format.colorSpace() == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
                return format;
            }
        }
        // Fallback to whatever is first
        return formats.get(0);
    }

    private int choosePresentMode(MemoryStack stack, boolean vSync) {
        if (vSync) {
            // FIFO is always guaranteed and provides v-sync
            return VK_PRESENT_MODE_FIFO_KHR;
        }

        var count = stack.mallocInt(1);
        vkGetPhysicalDeviceSurfacePresentModesKHR(device.physicalDevice(), surface, count, null);
        var modes = stack.mallocInt(count.get(0));
        vkGetPhysicalDeviceSurfacePresentModesKHR(device.physicalDevice(), surface, count, modes);

        // MAILBOX gives triple-buffering without tearing — prefer it when vSync is off
        for (int i = 0; i < count.get(0); i++) {
            if (modes.get(i) == VK_PRESENT_MODE_MAILBOX_KHR) {
                return VK_PRESENT_MODE_MAILBOX_KHR;
            }
        }
        return VK_PRESENT_MODE_FIFO_KHR;
    }

    private VkExtent2D chooseExtent(VkSurfaceCapabilitiesKHR capabilities, MemoryStack stack) {
        if (capabilities.currentExtent().width() != 0xFFFFFFFF) {
            // Surface has a fixed extent — use it
            return capabilities.currentExtent();
        }

        // Query the actual framebuffer size from GLFW (accounts for HiDPI scaling)
        var width = stack.mallocInt(1);
        var height = stack.mallocInt(1);
        glfwGetFramebufferSize(windowHandle, width, height);

        var actual = VkExtent2D.malloc(stack)
                .width(Math.clamp(
                        width.get(0),
                        capabilities.minImageExtent().width(),
                        capabilities.maxImageExtent().width()))
                .height(Math.clamp(
                        height.get(0),
                        capabilities.minImageExtent().height(),
                        capabilities.maxImageExtent().height()));

        return actual;
    }

    private void destroyFramebuffers() {
        if (framebuffers != null) {
            for (long fb : framebuffers) {
                vkDestroyFramebuffer(device.device(), fb, null);
            }
            framebuffers = null;
        }
    }

    private void destroyImageViews() {
        if (imageViews != null) {
            for (long view : imageViews) {
                vkDestroyImageView(device.device(), view, null);
            }
            imageViews = null;
        }
    }

    @Override
    public void dispose() {
        destroyFramebuffers();
        destroyImageViews();
        vkDestroySwapchainKHR(device.device(), swapChain, null);
        if (extent != null) {
            extent.free();
        }
        log.debug("VkSwapchainKHR destroyed");
    }
}
