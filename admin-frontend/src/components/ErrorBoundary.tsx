import { Component, type ErrorInfo, type ReactNode } from "react";

// Catches render crashes in one screen so the rest of the portal keeps working
// (otherwise React unmounts everything -> blank white page mid-shift).
export default class ErrorBoundary extends Component<{ children: ReactNode }, { error: Error | null }> {
  state = { error: null as Error | null };

  static getDerivedStateFromError(error: Error) {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error("Screen crashed:", error, info.componentStack);
  }

  render() {
    if (!this.state.error) return this.props.children;
    return (
      <div role="alert" className="m-6 rounded-[24px] bg-card p-6">
        <h1 className="text-xl font-bold">This screen hit an unexpected error</h1>
        <p className="mt-2 text-sm text-muted">Nothing was saved by this crash. Reload to try again; if it repeats, note what you clicked and tell the developer.</p>
        <pre className="mono mt-3 overflow-x-auto rounded-[14px] bg-background p-3 text-xs">{this.state.error.message}</pre>
        <button className="mt-4 rounded-full bg-primary px-5 py-2.5 text-sm font-semibold text-white" onClick={() => location.reload()}>Reload</button>
      </div>
    );
  }
}
