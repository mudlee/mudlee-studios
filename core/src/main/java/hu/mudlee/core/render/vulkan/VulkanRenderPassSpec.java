package hu.mudlee.core.render.vulkan;

record VulkanRenderPassSpec(int colorFormat, int initialLayout, int finalLayout, int colorLoadOp) {}
