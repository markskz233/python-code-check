document.addEventListener('DOMContentLoaded', function() {
    // 初始化错误统计图表
    initMistakesChart();
});

// 初始化错误统计图表
function initMistakesChart() {
    const syntaxErrorsCount = document.querySelectorAll('#syntaxErrorsAccordion .accordion-item').length;
    const logicErrorsCount = document.querySelectorAll('#logicErrorsAccordion .accordion-item').length;

    const ctx = document.getElementById('mistakesChart').getContext('2d');
    new Chart(ctx, {
        type: 'doughnut',
        data: {
            labels: ['语法错误', '逻辑错误'],
            datasets: [{
                data: [syntaxErrorsCount, logicErrorsCount],
                backgroundColor: ['#dc3545', '#ffc107'],
                borderWidth: 1
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: true,
            animation: {
                duration: 0
            },
            plugins: {
                legend: {
                    position: 'bottom'
                }
            }
        }
    });
}



// 更新错题显示
function updateErrorsDisplay() {
    const remainingSyntaxErrors = document.querySelectorAll('#syntaxErrorsAccordion .accordion-item').length;
    const remainingLogicErrors = document.querySelectorAll('#logicErrorsAccordion .accordion-item').length;
    
    // 更新语法错误显示
    if (remainingSyntaxErrors === 0) {
        const syntaxContainer = document.querySelector('#syntaxErrorsAccordion').parentElement;
        syntaxContainer.innerHTML = '<p class="text-center">暂无语法错误记录</p>';
    }

    // 更新逻辑错误显示
    if (remainingLogicErrors === 0) {
        const logicContainer = document.querySelector('#logicErrorsAccordion').parentElement;
        logicContainer.innerHTML = '<p class="text-center">暂无逻辑错误记录</p>';
    }

    // 如果两种错误都为空，更新AI建议区域
    if (remainingSyntaxErrors === 0 && remainingLogicErrors === 0) {
        const aiSuggestionContent = document.querySelector('.ai-suggestion-content');
        if (aiSuggestionContent) {
            aiSuggestionContent.innerHTML = '<p class="text-center text-muted">目前还没有错题记录，继续加油！</p>';
        }
    }
}

// 更新错误统计图表
function updateMistakesChart() {
    const syntaxErrorsCount = document.querySelectorAll('#syntaxErrorsAccordion .accordion-item').length;
    const logicErrorsCount = document.querySelectorAll('#logicErrorsAccordion .accordion-item').length;

    const chartCanvas = document.getElementById('mistakesChart');
    if (chartCanvas) {
        const chart = Chart.getChart(chartCanvas);
        if (chart) {
            chart.data.datasets[0].data = [syntaxErrorsCount, logicErrorsCount];
            chart.update('none');
        }
    }
}

// 删除错题
function deleteMistake(mistakeId) {
    const token = document.querySelector('meta[name="_csrf"]').getAttribute('content');
    const header = document.querySelector('meta[name="_csrf_header"]').getAttribute('content');

    if (!mistakeId || !token || !header) {
        showToast('删除请求无效，请刷新页面后重试', 'error');
        return;
    }

    fetch(`/mistakes/${mistakeId}`, {
        method: 'DELETE',
        headers: {
            'Content-Type': 'application/json',
            [header]: token
        }
    })
    .then(response => {
        if (response.ok) {
            // 删除成功后移除对应的错题元素
            const mistakeItem = document.querySelector(`[data-mistake-id="${mistakeId}"]`);
            if (mistakeItem) {
                mistakeItem.remove();
                updateErrorsDisplay();
                updateMistakesChart();
                showToast('错题删除成功', 'success');
            }
        } else {
            throw new Error('删除失败');
        }
    })
    .catch(error => {
        console.error('Error:', error);
        showToast('删除错题失败，请稍后重试', 'error');
    });
}

// 显示Toast提示
function showToast(message, type = 'info') {
    // 移除现有的toast
    const existingToast = document.querySelector('.toast');
    if (existingToast) {
        existingToast.remove();
    }

    // 创建新的toast
    const toastDiv = document.createElement('div');
    toastDiv.className = 'toast position-fixed bottom-0 end-0 m-3';
    toastDiv.setAttribute('role', 'alert');
    toastDiv.setAttribute('aria-live', 'assertive');
    toastDiv.setAttribute('aria-atomic', 'true');

    // 设置toast样式
    const bgClass = type === 'success' ? 'bg-success' :
                   type === 'error' ? 'bg-danger' :
                   type === 'info' ? 'bg-info' : 'bg-warning';

    toastDiv.innerHTML = `
        <div class="toast-header ${bgClass} text-white">
            <strong class="me-auto">提示</strong>
            <button type="button" class="btn-close btn-close-white" data-bs-dismiss="toast" aria-label="Close"></button>
        </div>
        <div class="toast-body">
            ${message}
        </div>
    `;

    document.body.appendChild(toastDiv);
    const toast = new bootstrap.Toast(toastDiv, {
        animation: true,
        autohide: true,
        delay: 3000
    });
    toast.show();
}