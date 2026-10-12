/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState } from 'react';
import { Smartphone, Shield, Send, Database, Github, CheckCircle2, RefreshCw, Terminal, Cpu } from 'lucide-react';

export default function App() {
  const [testSender, setTestSender] = useState('+8801700000000');
  const [testMessage, setTestMessage] = useState('Your bKash payment of 500 BDT to merchant was successful. TrxID: 9N87ABC42');
  const [userId, setUserId] = useState('vendor_12345');
  const [supabaseUrl] = useState('https://your-project.supabase.co/rest/v1/sms_forwarder');
  const [logs, setLogs] = useState<string[]>([
    'System initialized. Waiting for incoming SMS...',
    'Supabase configured via GitHub Actions Secrets.'
  ]);
  const [isSimulating, setIsSimulating] = useState(false);

  const simulateForwarding = () => {
    setIsSimulating(true);
    const txnMatch = testMessage.match(/(?:TrxID|TxnId|Trx ID)[:\s#]*([A-Za-z0-9]+)/i);
    const txnId = txnMatch ? txnMatch[1] : 'N/A';
    
    setTimeout(() => {
      setLogs(prev => [
        `[${new Date().toLocaleTimeString()}] SMS received from ${testSender}`,
        `[${new Date().toLocaleTimeString()}] Parsed TxnId: ${txnId}`,
        `[${new Date().toLocaleTimeString()}] POST -> ${supabaseUrl} (user_id: ${userId})`,
        `[${new Date().toLocaleTimeString()}] Success (200 OK): Record inserted into sms_forwarder`,
        ...prev
      ]);
      setIsSimulating(false);
    }, 600);
  };

  return (
    <div className="min-h-screen bg-slate-900 text-slate-100 flex flex-col font-sans">
      {/* Header */}
      <header className="border-b border-slate-800 bg-slate-950/50 backdrop-blur sticky top-0 z-50 px-6 py-4 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 bg-emerald-600 rounded-xl flex items-center justify-center shadow-lg shadow-emerald-900/30">
            <Smartphone className="w-6 h-6 text-white" />
          </div>
          <div>
            <h1 className="text-xl font-bold tracking-tight text-white">SMS Forwarder</h1>
            <p className="text-xs text-emerald-400 font-medium">Android Kotlin • Supabase Sync • GitHub Actions</p>
          </div>
        </div>
        <div className="flex items-center gap-3">
          <a
            href="https://github.com"
            target="_blank"
            rel="noreferrer"
            className="flex items-center gap-2 px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-lg text-sm font-medium transition"
          >
            <Github className="w-4 h-4" />
            <span>GitHub Actions Build</span>
          </a>
        </div>
      </header>

      {/* Main Content */}
      <main className="flex-1 max-w-7xl w-full mx-auto p-6 grid grid-cols-1 lg:grid-cols-3 gap-6">
        
        {/* Left Column: Info & Architecture */}
        <div className="lg:col-span-1 space-y-6">
          <div className="bg-slate-800/60 border border-slate-700/60 rounded-2xl p-6 shadow-xl">
            <h2 className="text-lg font-semibold text-white mb-3 flex items-center gap-2">
              <Shield className="w-5 h-5 text-emerald-400" />
              <span>Project Architecture</span>
            </h2>
            <ul className="space-y-3 text-sm text-slate-300">
              <li className="flex items-start gap-2">
                <CheckCircle2 className="w-4 h-4 text-emerald-400 mt-0.5 shrink-0" />
                <span><strong>Background Receiver:</strong> Wakes up instantly on `SMS_RECEIVED` to forward messages.</span>
              </li>
              <li className="flex items-start gap-2">
                <CheckCircle2 className="w-4 h-4 text-emerald-400 mt-0.5 shrink-0" />
                <span><strong>GitHub Secrets:</strong> Supabase URL and API keys are injected securely at build time via GitHub Actions.</span>
              </li>
              <li className="flex items-start gap-2">
                <CheckCircle2 className="w-4 h-4 text-emerald-400 mt-0.5 shrink-0" />
                <span><strong>Transaction Parser:</strong> Automatically extracts `txn_id`, `user_id`, `phone`, and `full_msg`.</span>
              </li>
              <li className="flex items-start gap-2">
                <CheckCircle2 className="w-4 h-4 text-emerald-400 mt-0.5 shrink-0" />
                <span><strong>Detailed Viewer:</strong> Dedicated messages activity with full history and search support.</span>
              </li>
            </ul>
          </div>

          <div className="bg-slate-800/60 border border-slate-700/60 rounded-2xl p-6 shadow-xl">
            <h2 className="text-lg font-semibold text-white mb-3 flex items-center gap-2">
              <Database className="w-5 h-5 text-emerald-400" />
              <span>Supabase Table Schema</span>
            </h2>
            <div className="bg-slate-900 rounded-xl p-3 font-mono text-xs text-emerald-300 space-y-1">
              <p className="text-slate-400">table: <span className="text-white">sms_forwarder</span></p>
              <p>• id (uuid / serial)</p>
              <p>• user_id (text)</p>
              <p>• txn_id (text)</p>
              <p>• phone (text)</p>
              <p>• full_msg (text)</p>
            </div>
          </div>
        </div>

        {/* Right 2 Columns: Live Simulator & Console */}
        <div className="lg:col-span-2 space-y-6">
          <div className="bg-slate-800/60 border border-slate-700/60 rounded-2xl p-6 shadow-xl">
            <h2 className="text-lg font-semibold text-white mb-4 flex items-center gap-2">
              <Cpu className="w-5 h-5 text-emerald-400" />
              <span>Live SMS Forwarding Simulator</span>
            </h2>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-4">
              <div>
                <label className="block text-xs font-medium text-slate-400 mb-1">Sender Number / Name</label>
                <input
                  type="text"
                  value={testSender}
                  onChange={e => setTestSender(e.target.value)}
                  className="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-sm text-slate-100 focus:outline-none focus:border-emerald-500"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-slate-400 mb-1">Vendor User ID</label>
                <input
                  type="text"
                  value={userId}
                  onChange={e => setUserId(e.target.value)}
                  className="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-sm text-slate-100 focus:outline-none focus:border-emerald-500"
                />
              </div>
            </div>

            <div className="mb-4">
              <label className="block text-xs font-medium text-slate-400 mb-1">SMS Message Body</label>
              <textarea
                rows={3}
                value={testMessage}
                onChange={e => setTestMessage(e.target.value)}
                className="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-sm text-slate-100 focus:outline-none focus:border-emerald-500"
              />
            </div>

            <button
              onClick={simulateForwarding}
              disabled={isSimulating}
              className="w-full py-3 bg-emerald-600 hover:bg-emerald-500 text-white font-semibold rounded-xl shadow-lg shadow-emerald-900/30 flex items-center justify-center gap-2 transition disabled:opacity-50"
            >
              {isSimulating ? (
                <>
                  <RefreshCw className="w-5 h-5 animate-spin" />
                  <span>Forwarding to Supabase...</span>
                </>
              ) : (
                <>
                  <Send className="w-5 h-5" />
                  <span>Simulate Incoming SMS Forward</span>
                </>
              )}
            </button>
          </div>

          {/* Console Output */}
          <div className="bg-slate-950 border border-slate-800 rounded-2xl p-6 shadow-xl font-mono">
            <div className="flex items-center justify-between mb-3 pb-2 border-b border-slate-800">
              <span className="text-xs text-slate-400 flex items-center gap-2">
                <Terminal className="w-4 h-4 text-emerald-400" />
                <span>Execution Logs & Supabase Payload Stream</span>
              </span>
              <button
                onClick={() => setLogs(['Console cleared.'])}
                className="text-xs text-slate-500 hover:text-slate-300"
              >
                Clear
              </button>
            </div>
            <div className="space-y-1.5 text-xs text-emerald-400/90 max-h-48 overflow-y-auto">
              {logs.map((log, idx) => (
                <div key={idx} className="break-all">{log}</div>
              ))}
            </div>
          </div>
        </div>

      </main>
    </div>
  );
}
