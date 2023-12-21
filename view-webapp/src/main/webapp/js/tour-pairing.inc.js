let focused = undefined;

onLoad(()=>{
  $('.listitem').on('click', e => {
    if (e.shiftKey && typeof(focused) !== 'undefined') {
      let from = focused.index('.listitem');
      let to = e.target.closest('.listitem').index('.listitem');
      if (from > to) {
        let tmp = from;
        from = to;
        to = tmp;
      }
      let parent = e.target.closest('.multi-select');
      let children = parent.childNodes.filter('.listitem');
      for (let j = from; j <= to; ++j) {
        children.item(j).addClass('selected');
        children.item(j).attr('draggable', true);
      }
    } else {
      focused = e.target.closest('.listitem').toggleClass('selected').attr('draggable', true);
    }
  });
});
