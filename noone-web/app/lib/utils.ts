import { type ClassValue, clsx } from "clsx";
import { twMerge } from "tailwind-merge";

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

export function downloadContent(content: Blob, fileName: string, fileExtension?: string) {
  const link = document.createElement("a");
  link.href = URL.createObjectURL(content);
  if (fileExtension) {
    link.download = `${fileName}${fileExtension}`;
  } else {
    link.download = fileName;
  }
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
}

export function base64ToBytes(base64String: string): Uint8Array {
  const byteNumbers = Array.from(atob(base64String), (char) => char.charCodeAt(0));
  return new Uint8Array(byteNumbers);
}

export function bytesToBase64(bytes: Uint8Array): string {
  const binary = Array.from(bytes, (b) => String.fromCharCode(b)).join("");
  return btoa(binary);
}

export function downloadBytes(base64String: string, className?: string, jarName?: string) {
  const byteArray = base64ToBytes(base64String);
  const blob = new Blob([byteArray as BlobPart], {
    type: className ? "application/java-vm" : "application/java-archive",
  });

  const link = document.createElement("a");
  link.href = window.URL.createObjectURL(blob);
  link.download = className
    ? `${className.substring(className.lastIndexOf("."))}.class`
    : `${jarName}.jar`;

  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
}

export const formatBytes = (bytes: number, decimals = 2) => {
  if (!bytes) return "0 Bytes";
  const k = 1024;
  const dm = decimals < 0 ? 0 : decimals;
  const sizes = ["Bytes", "KB", "MB", "GB", "TB"];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + " " + sizes[i];
};

export const formatUptime = (ms: number) => {
  const seconds = Math.floor(ms / 1000);
  const minutes = Math.floor(seconds / 60);
  const hours = Math.floor(minutes / 60);
  const days = Math.floor(hours / 24);

  if (days > 0) return `${days}d ${hours % 24}h`;
  if (hours > 0) return `${hours}h ${minutes % 60}m`;
  if (minutes > 0) return `${minutes}m ${seconds % 60}s`;
  return `${seconds}s`;
};

export function notNeedUrlPattern(shellType: string | undefined) {
  if (shellType === undefined) {
    return false;
  }
  return (
    shellType.endsWith("Listener") ||
    shellType.endsWith("Valve") ||
    shellType.startsWith("Agent") ||
    shellType.endsWith("Interceptor") ||
    shellType.endsWith("Handler") ||
    shellType.endsWith("WebFilter") ||
    shellType === "Customizer"
  );
}
