import { config } from "@/shared/lib/config";
import { UploadTask } from "./UploadTask";
export class UploadScheduler {
  private tasks: UploadTask[] = []; private active = 0; private cursor = 0; private pumping = false;
  private listeners = new Set<() => void>();
  subscribe(listener: () => void) { this.listeners.add(listener); return () => { this.listeners.delete(listener); }; }
  notify = () => { this.listeners.forEach(listener => listener()); this.pump(); };
  getTasks() { return this.tasks; }
  add(task: UploadTask) { this.tasks.push(task); this.notify(); void this.startPending(); }
  private async startPending() {
    const preparing = this.tasks.filter(task => task.status === "preparing" || task.status === "uploading" || task.status === "paused" || task.status === "completing").length;
    for (const task of this.tasks.filter(task => task.status === "pending").slice(0, Math.max(0, config.maxActiveFiles - preparing))) void task.start().then(() => this.startPending());
  }
  private pump() {
    if (this.pumping) return;
    this.pumping = true;
    queueMicrotask(() => {
      this.pumping = false;
      while (this.active < config.maxInflightParts && this.tasks.length) {
        let selected: UploadTask | undefined;
        for (let i = 0; i < this.tasks.length; i++) {
          const index = (this.cursor + i) % this.tasks.length;
          if (this.tasks[index].canSchedule) { selected = this.tasks[index]; this.cursor = (index + 1) % this.tasks.length; break; }
        }
        if (!selected) break;
        this.active++;
        void selected.uploadNext().finally(() => { this.active--; this.notify(); void this.startPending(); });
      }
    });
  }
}
export const uploadScheduler = new UploadScheduler();
