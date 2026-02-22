package hu.mudlee.core.render.vulkan;

import hu.mudlee.core.Disposable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.HashSet;
import java.util.Set;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME;
import static org.lwjgl.vulkan.VK12.*;

class VulkanDevice implements Disposable {

  private static final Logger log = LoggerFactory.getLogger(VulkanDevice.class);
  private static final Set<String> REQUIRED_DEVICE_EXTENSIONS = Set.of(VK_KHR_SWAPCHAIN_EXTENSION_NAME);

  /**
   * Indices for the queue families needed for rendering and presentation.
   * graphicsFamily: submits draw commands.
   * presentFamily: presents rendered images to the surface.
   * These may or may not be the same family depending on the GPU.
   */
  record QueueFamilyIndices(int graphicsFamily, int presentFamily) {
    boolean isComplete() {
      return graphicsFamily >= 0 && presentFamily >= 0;
    }
  }

  private final VkPhysicalDevice physicalDevice;
  private final VkDevice logicalDevice;
  private final VkQueue graphicsQueue;
  private final VkQueue presentQueue;
  private final QueueFamilyIndices queueFamilyIndices;
  private final VkPhysicalDeviceMemoryProperties memoryProperties;

  VulkanDevice(VkInstance instance, long surface) {
    physicalDevice = selectPhysicalDevice(instance, surface);
    queueFamilyIndices = findQueueFamilies(physicalDevice, surface);
    logicalDevice = createLogicalDevice();
    graphicsQueue = retrieveQueue(queueFamilyIndices.graphicsFamily());
    presentQueue = retrieveQueue(queueFamilyIndices.presentFamily());

    // Heap-allocated because it's referenced across many frames
    memoryProperties = VkPhysicalDeviceMemoryProperties.malloc();
    vkGetPhysicalDeviceMemoryProperties(physicalDevice, memoryProperties);
  }

  VkPhysicalDevice physicalDevice() {
    return physicalDevice;
  }

  VkDevice device() {
    return logicalDevice;
  }

  VkQueue graphicsQueue() {
    return graphicsQueue;
  }

  VkQueue presentQueue() {
    return presentQueue;
  }

  QueueFamilyIndices queueFamilyIndices() {
    return queueFamilyIndices;
  }

  VkPhysicalDeviceMemoryProperties memoryProperties() {
    return memoryProperties;
  }

  void waitIdle() {
    vkDeviceWaitIdle(logicalDevice);
  }

  private VkPhysicalDevice selectPhysicalDevice(VkInstance instance, long surface) {
    try (MemoryStack stack = stackPush()) {
      IntBuffer count = stack.mallocInt(1);
      vkEnumeratePhysicalDevices(instance, count, null);
      if (count.get(0) == 0) {
        throw new RuntimeException("No Vulkan-capable GPU found");
      }

      PointerBuffer pDevices = stack.mallocPointer(count.get(0));
      vkEnumeratePhysicalDevices(instance, count, pDevices);

      VkPhysicalDevice best = null;
      long bestScore = Long.MIN_VALUE;

      for (int i = 0; i < pDevices.capacity(); i++) {
        VkPhysicalDevice candidate = new VkPhysicalDevice(pDevices.get(i), instance);
        long score = scoreDevice(candidate, surface, stack);
        if (score > bestScore) {
          bestScore = score;
          best = candidate;
        }
      }

      if (best == null || bestScore < 0) {
        throw new RuntimeException("No suitable Vulkan GPU found");
      }

      VkPhysicalDeviceProperties props = VkPhysicalDeviceProperties.malloc(stack);
      vkGetPhysicalDeviceProperties(best, props);
      log.debug("Selected GPU: {} (score={})", props.deviceNameString(), bestScore);
      return best;
    }
  }

  private long scoreDevice(VkPhysicalDevice device, long surface, MemoryStack stack) {
    // Disqualify if required queues or extensions are missing
    QueueFamilyIndices families = findQueueFamilies(device, surface);
    if (!families.isComplete()) return Long.MIN_VALUE;
    if (!supportsRequiredExtensions(device, stack)) return Long.MIN_VALUE;

    // Disqualify if swap chain support is inadequate
    IntBuffer count = stack.mallocInt(1);
    vkGetPhysicalDeviceSurfaceFormatsKHR(device, surface, count, null);
    if (count.get(0) == 0) return Long.MIN_VALUE;
    vkGetPhysicalDeviceSurfacePresentModesKHR(device, surface, count.position(0), null);
    if (count.get(0) == 0) return Long.MIN_VALUE;

    VkPhysicalDeviceProperties props = VkPhysicalDeviceProperties.malloc(stack);
    vkGetPhysicalDeviceProperties(device, props);

    // Compute score: discrete GPU preferred, VRAM as tiebreaker
    VkPhysicalDeviceMemoryProperties memProps = VkPhysicalDeviceMemoryProperties.malloc(stack);
    vkGetPhysicalDeviceMemoryProperties(device, memProps);
    long vramMB = 0;
    for (int i = 0; i < memProps.memoryHeapCount(); i++) {
      if ((memProps.memoryHeaps(i).flags() & VK_MEMORY_HEAP_DEVICE_LOCAL_BIT) != 0) {
        vramMB = memProps.memoryHeaps(i).size() / (1024 * 1024);
        break;
      }
    }

    long score = vramMB;
    if (props.deviceType() == VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU) {
      score += 100_000L;
    }
    return score;
  }

  private boolean supportsRequiredExtensions(VkPhysicalDevice device, MemoryStack stack) {
    IntBuffer count = stack.mallocInt(1);
    vkEnumerateDeviceExtensionProperties(device, (String) null, count, null);
    VkExtensionProperties.Buffer available = VkExtensionProperties.malloc(count.get(0), stack);
    vkEnumerateDeviceExtensionProperties(device, (String) null, count, available);

    Set<String> remaining = new HashSet<>(REQUIRED_DEVICE_EXTENSIONS);
    for (VkExtensionProperties ext : available) {
      remaining.remove(ext.extensionNameString());
    }
    return remaining.isEmpty();
  }

  static QueueFamilyIndices findQueueFamilies(VkPhysicalDevice device, long surface) {
    try (MemoryStack stack = stackPush()) {
      IntBuffer count = stack.mallocInt(1);
      vkGetPhysicalDeviceQueueFamilyProperties(device, count, null);

      VkQueueFamilyProperties.Buffer families = VkQueueFamilyProperties.malloc(count.get(0), stack);
      vkGetPhysicalDeviceQueueFamilyProperties(device, count, families);

      int graphicsFamily = -1;
      int presentFamily = -1;
      IntBuffer presentSupport = stack.mallocInt(1);

      for (int i = 0; i < families.capacity(); i++) {
        if ((families.get(i).queueFlags() & VK_QUEUE_GRAPHICS_BIT) != 0) {
          graphicsFamily = i;
        }

        vkGetPhysicalDeviceSurfaceSupportKHR(device, i, surface, presentSupport);
        if (presentSupport.get(0) == VK_TRUE) {
          presentFamily = i;
        }

        if (graphicsFamily >= 0 && presentFamily >= 0) break;
      }

      return new QueueFamilyIndices(graphicsFamily, presentFamily);
    }
  }

  private VkDevice createLogicalDevice() {
    try (MemoryStack stack = stackPush()) {
      // Use a single queue create info if both families are the same
      boolean sharedFamily = queueFamilyIndices.graphicsFamily() == queueFamilyIndices.presentFamily();
      int[] uniqueFamilies = sharedFamily
        ? new int[]{queueFamilyIndices.graphicsFamily()}
        : new int[]{queueFamilyIndices.graphicsFamily(), queueFamilyIndices.presentFamily()};

      FloatBuffer priority = stack.floats(1.0f);
      VkDeviceQueueCreateInfo.Buffer queueInfos = VkDeviceQueueCreateInfo.calloc(uniqueFamilies.length, stack);
      for (int i = 0; i < uniqueFamilies.length; i++) {
        queueInfos.get(i)
          .sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
          .queueFamilyIndex(uniqueFamilies[i])
          .pQueuePriorities(priority);
      }

      PointerBuffer extensions = stack.mallocPointer(REQUIRED_DEVICE_EXTENSIONS.size());
      for (String ext : REQUIRED_DEVICE_EXTENSIONS) {
        extensions.put(stack.ASCII(ext));
      }
      extensions.rewind();

      VkDeviceCreateInfo createInfo = VkDeviceCreateInfo.calloc(stack)
        .sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
        .pQueueCreateInfos(queueInfos)
        .ppEnabledExtensionNames(extensions)
        .pEnabledFeatures(VkPhysicalDeviceFeatures.calloc(stack));

      PointerBuffer pDevice = stack.mallocPointer(1);
      if (vkCreateDevice(physicalDevice, createInfo, null, pDevice) != VK_SUCCESS) {
        throw new RuntimeException("Failed to create VkDevice");
      }

      log.debug("VkDevice created");
      return new VkDevice(pDevice.get(0), physicalDevice, createInfo);
    }
  }

  private VkQueue retrieveQueue(int familyIndex) {
    try (MemoryStack stack = stackPush()) {
      PointerBuffer pQueue = stack.mallocPointer(1);
      vkGetDeviceQueue(logicalDevice, familyIndex, 0, pQueue);
      return new VkQueue(pQueue.get(0), logicalDevice);
    }
  }

  @Override
  public void dispose() {
    memoryProperties.free();
    vkDestroyDevice(logicalDevice, null);
    log.debug("VkDevice destroyed");
  }
}
